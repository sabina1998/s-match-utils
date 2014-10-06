package it.unitn.disi.smatch.loaders.context;

import it.unitn.disi.smatch.data.trees.Context;
import it.unitn.disi.smatch.data.trees.IContext;
import it.unitn.disi.smatch.data.trees.INode;
import it.unitn.disi.smatch.loaders.ILoader;
import org.semanticweb.HermiT.Reasoner;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.skos.SKOSConcept;
import org.semanticweb.skos.SKOSCreationException;
import org.semanticweb.skos.SKOSDataset;
import org.semanticweb.skos.SKOSLiteral;
import org.semanticweb.skos.properties.SKOSAltLabelProperty;
import org.semanticweb.skos.properties.SKOSPrefLabelProperty;
import org.semanticweb.skosapibinding.SKOSManager;
import org.semanticweb.skosapibinding.SKOSReasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads a context from a SKOS file using SKOS API (based on OWL API) and HermiT reasoner.
 * Takes in a preferredLanguage parameter, which defines the language which should be preferred for labels.
 * If specified, the loader will search for the label in the specified language among preferred labels and then among
 * alternative labels.
 *
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class SKOSContextLoader extends BaseContextLoader<IContext, INode> implements IContextLoader {

    private static final Logger log = LoggerFactory.getLogger(SKOSContextLoader.class);

    // which language to load, default "" - load anything
    private final String preferredLanguage;

    private final boolean precompute;

    public SKOSContextLoader() {
        preferredLanguage = "";
        precompute = true;
    }

    public SKOSContextLoader(String preferredLanguage, boolean precompute) {
        this.preferredLanguage = preferredLanguage;
        this.precompute = precompute;
    }

    public IContext loadContext(String fileName) throws ContextLoaderException {
        IContext result = new Context();
        try {
            //check location whether it is URL or not
            if (!fileName.startsWith("http://") && !fileName.startsWith("file://")) {
                File f = new File(fileName);
                fileName = "file:///" + f.getAbsolutePath().replace('\\', '/');
            }
            SKOSManager manager = new SKOSManager();
            SKOSDataset dataSet = manager.loadDatasetFromPhysicalIRI(IRI.create(fileName));
            SKOSReasoner reasoner = new SKOSReasoner(manager, new Reasoner.ReasonerFactory());
            reasoner.loadDataset(dataSet);
            if (precompute) {
                reasoner.classify();
            }

            // IRI - INode
            Map<String, INode> conceptNode = new HashMap<>();
            Set<SKOSConcept> skosConcepts = reasoner.getSKOSConcepts();
            if (log.isInfoEnabled()) {
                log.info("Loaded SKOS concepts: " + skosConcepts.size());
            }

            int unlabeledNodeCount = 0;
            // create a node for each class
            for (SKOSConcept concept : skosConcepts) {
                if (log.isDebugEnabled()) {
                    log.debug("Importing: " + concept.getIRI());
                }

                String nodeName = "";

                // get a node name from pref labels
                SKOSPrefLabelProperty prefLabelProperty = manager.getSKOSDataFactory().getSKOSPrefLabelProperty();
                for (SKOSLiteral literal : concept.getSKOSRelatedConstantByProperty(dataSet, prefLabelProperty)) {
                    if (null != preferredLanguage && preferredLanguage.isEmpty()) {
                        nodeName = literal.getLiteral();
                        break;
                    } else {
                        if (preferredLanguage.equals(literal.getAsSKOSUntypedLiteral().getLang())) {
                            nodeName = literal.getLiteral();
                            break;
                        }
                    }
                }

                // get a node name from alt labels
                if (null != nodeName && nodeName.isEmpty()) {
                    SKOSAltLabelProperty altLabelProperty = manager.getSKOSDataFactory().getSKOSAltLabelProperty();
                    for (SKOSLiteral literal : concept.getSKOSRelatedConstantByProperty(dataSet, altLabelProperty)) {
                        if (preferredLanguage.isEmpty()) {
                            nodeName = literal.getLiteral();
                            break;
                        } else {
                            if (preferredLanguage.equals(literal.getAsSKOSUntypedLiteral().getLang())) {
                                nodeName = literal.getLiteral();
                                break;
                            }
                        }
                    }
                }

                if (null != nodeName && nodeName.isEmpty()) {
                    unlabeledNodeCount++;
                    if (log.isWarnEnabled()) {
                        log.warn("Label is not found in language " + preferredLanguage + " for a concept: " + concept.getIRI());
                        log.warn("Creating unlabeled node...");
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Creating a node: " + nodeName);
                    }
                }

                INode node = result.createNode(nodeName);
                node.getNodeData().setProvenance(concept.getIRI().toString());
                node.setUserObject(concept);
                conceptNode.put(concept.getIRI().toString(), node);
            }

            if (0 < unlabeledNodeCount) {
                if (log.isInfoEnabled()) {
                    log.info("Created unlabeled nodes: " + unlabeledNodeCount);
                }
            }

            if (log.isInfoEnabled()) {
                log.info("Creating hierarchy via BTs...");
            }

            int countBT = 0;
            int linksCreated = 0;

            // create hierarchy via BTs
            for (Map.Entry<String, INode> e : conceptNode.entrySet()) {
                String conceptIRI = e.getKey();
                INode child = e.getValue();
                if (log.isDebugEnabled()) {
                    log.debug("Creating hierarchy: " + child.getNodeData().getName() + ": " + conceptIRI);
                }

                Set<SKOSConcept> parents = reasoner.getSKOSBroaderConcepts(dataSet.getSKOSEntity(conceptIRI).asSKOSConcept());
                countBT = countBT + parents.size();

                // there can be multiple BTs
                // either discard, or duplicate the subtree in two places
                // discard for now all but one
                if (1 < parents.size()) {
                    if (log.isWarnEnabled()) {
                        log.warn("Multiple BTs are found for the concept: " + conceptIRI);
                    }
                }
                if (parents.iterator().hasNext()) {
                    SKOSConcept parentConcept = parents.iterator().next();
                    INode parentNode = conceptNode.get(parentConcept.getIRI().toString());
                    if (log.isDebugEnabled()) {
                        log.debug("Choosing as a parent BT: " + parentConcept.getIRI());
                        log.debug(child.getNodeData().getName() + " -> BT -> " + parentNode.getNodeData().getName());
                    }

                    parentNode.addChild(child);
                    linksCreated++;
                }
            }

            if (log.isInfoEnabled()) {
                log.info("Encountered BTs: " + countBT);
                log.info("Parent-child relations established: " + linksCreated);
                log.info("Creating hierarchy via NTs...");
            }

            int countNT = 0;
            linksCreated = 0;

            // create hierarchy via NTs
            for (Map.Entry<String, INode> e : conceptNode.entrySet()) {
                String conceptIRI = e.getKey();
                INode parent = e.getValue();
                if (log.isDebugEnabled()) {
                    log.debug("Creating hierarchy: " + parent.getNodeData().getName() + ": " + conceptIRI);
                }

                Set<SKOSConcept> children = reasoner.getSKOSNarrowerConcepts(dataSet.getSKOSEntity(conceptIRI).asSKOSConcept());
                countNT = countNT + children.size();

                for (SKOSConcept childConcept : children) {
                    INode child = conceptNode.get(childConcept.getIRI().toString());
                    // there can be multiple BTs
                    // either discard, or duplicate the subtree in two places
                    // discard for now all but one
                    if (child.hasParent()) {
                        if (log.isWarnEnabled()) {
                            log.warn("Keeping previously set parent: child: " + child.getNodeData().getName() + " -> parent: " + child.getParent().getNodeData().getName());
                            log.warn("Multiple BTs are found for the concept: " + conceptIRI);
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Choosing as a parent BT: " + conceptIRI);
                            log.debug(parent.getNodeData().getName() + " -> NT -> " + child.getNodeData().getName());
                        }

                        int childIndex = parent.getChildIndex(child);
                        if (-1 == childIndex) {
                            if (!checkCycle(parent, child)) {
                                parent.addChild(child);
                                linksCreated++;
                            } else {
                                log.warn("Cycle found: " + parent.getNodeData().getName() + " -> " + child.getNodeData().getName());
                            }
                        } else {
                            if (log.isWarnEnabled()) {
                                log.warn("Child already exist under this parent: " + parent.getNodeData().getName() + " -> child -> " + child.getNodeData().getName());
                                log.warn("Duplicated NT or label: " + conceptIRI + " -> NT -> " + childConcept.getIRI());
                            }
                        }
                    }
                }
            }

            if (log.isInfoEnabled()) {
                log.info("Encountered NTs: " + countNT);
                log.info("Parent-child relations established: " + linksCreated);
            }


            if (log.isInfoEnabled()) {
                log.info("Checking multiple roots...");
            }
            // check multiple roots
            Set<INode> roots = new HashSet<>();
            for (SKOSConcept concept : skosConcepts) {
                INode node = conceptNode.get(concept.getIRI().toString());
                if (!node.hasParent()) {
                    roots.add(node);
                }
            }

            if (log.isWarnEnabled()) {
                log.warn("Found root nodes: " + roots.size());
                if (log.isDebugEnabled()) {
                    for (INode r : roots) {
                        log.debug("Root: " + r.getNodeData().getName());
                    }
                }
            }

            if (1 < roots.size()) {
                if (log.isWarnEnabled()) {
                    log.warn("Found multiple roots. Creating artificial root: Top");
                }

                // create artificial Top root
                INode root = result.createRoot("Top");
                // put every other top one under it
                for (INode r : roots) {
                    root.addChild(r);
                }
            } else {
                if (1 == roots.size()) {
                    result.setRoot(roots.iterator().next());
                } else {
                    throw new ContextLoaderException("Cannot find even one root.");
                }
            }

            createIds(result);
        } catch (SKOSCreationException e) {
            throw new ContextLoaderException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }

        return result;
    }

    private boolean checkCycle(INode parent, INode child) {
        INode ancestor = parent;

        do {
            if (ancestor == child) {
                return true;
            }
        } while ((ancestor = ancestor.getParent()) != null);

        return false;
    }

    public String getDescription() {
        return ILoader.SKOS_FILES;
    }

    public ILoader.LoaderType getType() {
        return ILoader.LoaderType.FILE;
    }
}