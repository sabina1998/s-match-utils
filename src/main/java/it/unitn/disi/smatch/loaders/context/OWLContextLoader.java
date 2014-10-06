package it.unitn.disi.smatch.loaders.context;

import it.unitn.disi.smatch.data.trees.Context;
import it.unitn.disi.smatch.data.trees.IContext;
import it.unitn.disi.smatch.data.trees.INode;
import it.unitn.disi.smatch.loaders.ILoader;
import org.semanticweb.HermiT.Reasoner;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Set;

/**
 * Loads a context from an ontology OWL API and HermiT reasoner.
 * Takes in a topClass parameter, which defines the starting class. If not specified, the Thing will be used.
 * Takes in an excludeNothing parameter, which specifies whether to exclude Nothing class. Default true.
 * Takes in a replaceUnderscore parameter, which specifies whether to replace _ in class names. Default true.
 * <p/>
 * The loader uses class hierarchy from the ontology in question and loads it into a tree.
 * Currently, the loader follows subclass hierarchy.
 *
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class OWLContextLoader extends BaseContextLoader<IContext, INode> implements IContextLoader {

    private static final Logger log = LoggerFactory.getLogger(OWLContextLoader.class);

    // class to start from, if not specified, a Thing will be used
    private final String topClass;

    // whether to exclude Nothing class
    private final boolean excludeNothing;

    // whether to replace _
    private final boolean replaceUnderscore;

    private static final OWLClass NOTHING_CLASS = OWLManager.getOWLDataFactory().getOWLClass(OWLRDFVocabulary.OWL_NOTHING.getIRI());

    public OWLContextLoader() {
        topClass = null;
        excludeNothing = true;
        replaceUnderscore = true;
    }

    public OWLContextLoader(String topClass, boolean excludeNothing, boolean replaceUnderscore) {
        this.topClass = topClass;
        this.excludeNothing = excludeNothing;
        this.replaceUnderscore = replaceUnderscore;
    }

    /**
     * <p>Simple visitor that grabs any labels on an entity.</p>
     * <p/>
     * Author: Sean Bechhofer<br>
     * The University Of Manchester<br>
     * Information Management Group<br>
     * Date: 17-03-2007<br>
     * <br>
     */
    private class LabelExtractor implements OWLAnnotationObjectVisitor {

        String result;

        public LabelExtractor() {
            result = null;
        }

        public void visit(OWLAnonymousIndividual individual) {
        }

        public void visit(IRI iri) {
        }

        public void visit(OWLLiteral literal) {
        }

        public void visit(OWLAnnotation annotation) {
            /*
            * If it's a label, grab it as the result. Note that if there are
            * multiple labels, the last one will be used.
            */
            if (annotation.getProperty().isLabel()) {
                OWLLiteral c = (OWLLiteral) annotation.getValue();
                result = c.getLiteral();
            }

        }

        public void visit(OWLAnnotationAssertionAxiom axiom) {
        }

        public void visit(OWLAnnotationPropertyDomainAxiom axiom) {
        }

        public void visit(OWLAnnotationPropertyRangeAxiom axiom) {
        }

        public void visit(OWLSubAnnotationPropertyOfAxiom axiom) {
        }

        public void visit(OWLAnnotationProperty property) {
        }

        public void visit(OWLAnnotationValue value) {
        }


        public String getResult() {
            return result;
        }
    }

    public void buildHierarchy(OWLReasoner reasoner, OWLOntology o, IContext c, INode root, OWLClass clazz) throws OWLException {
        if (reasoner.isSatisfiable(clazz)) {
            if (1 < reasoner.getSuperClasses(clazz, true).getFlattened().size()) {
                if (log.isWarnEnabled()) {
                    log.warn("Multiple superclasses:\t" + clazz.toStringID());
                }
            }
            for (OWLClass childClass : reasoner.getSubClasses(clazz, true).getFlattened()) {
                if (!excludeNothing || !NOTHING_CLASS.equals(childClass)) {
                    if (!childClass.equals(clazz)) {
                        INode childNode = c.createNode(labelFor(o, childClass));
                        childNode.getNodeData().setProvenance(childClass.getIRI().toString());
                        root.addChild(childNode);
                        buildHierarchy(reasoner, o, c, childNode, childClass);
                    } else {
                        if (log.isWarnEnabled()) {
                            log.warn("Subclass equal to class:\t" + clazz.toStringID());
                        }
                    }
                }
            }
        }
    }

    public IContext loadContext(String fileName) throws ContextLoaderException {
        IContext result = new Context();
        try {
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            //check location whether it is URL or not
            if (!fileName.startsWith("http://") && !fileName.startsWith("file://")) {
                File f = new File(fileName);
                fileName = "file:///" + f.getAbsolutePath().replace('\\', '/');
            }
            IRI iri = IRI.create(fileName);
            OWLOntology o = manager.loadOntologyFromOntologyDocument(iri);

            OWLReasonerFactory reasonerFactory = new Reasoner.ReasonerFactory();
            OWLReasoner reasoner = reasonerFactory.createReasoner(o);
            reasoner.precomputeInferences();

            OWLClass top = null;
            if (null != topClass) {
                if (-1 == topClass.indexOf('#')) {
                    top = manager.getOWLDataFactory().getOWLClass(IRI.create(o.getOntologyID().getOntologyIRI() + "#" + topClass));
                } else {
                    top = manager.getOWLDataFactory().getOWLClass(IRI.create(topClass));
                }
            }
            if (null == top) {
                IRI classIRI = OWLRDFVocabulary.OWL_THING.getIRI();
                top = manager.getOWLDataFactory().getOWLClass(classIRI);
            }

            buildHierarchy(reasoner, o, result, result.createRoot(labelFor(o, top)), top);

            /* Now any unsatisfiable classes */
            for (OWLClass cl : o.getClassesInSignature()) {
                if (!reasoner.isSatisfiable(cl)) {
                    INode node = result.createNode(labelFor(o, cl));
                    node.getNodeData().setProvenance(cl.getIRI().toString());
                    result.getRoot().addChild(node);
                }
            }

            createIds(result);
        } catch (OWLException e) {
            throw new ContextLoaderException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }

        return result;
    }

    public String getDescription() {
        return ILoader.OWL_FILES;
    }

    public ILoader.LoaderType getType() {
        return ILoader.LoaderType.FILE;
    }

    private String labelFor(OWLOntology ontology, OWLClass clazz) {
        String result;
        LabelExtractor le = new LabelExtractor();
        Set<OWLAnnotation> annotations = clazz.getAnnotations(ontology);
        for (OWLAnnotation anno : annotations) {
            anno.accept(le);
        }
        /* Print out the label if there is one. If not, just use the class URI */
        if (le.getResult() != null) {
            result = le.getResult();
        } else {
            if (null != clazz.getIRI().getFragment() && !clazz.getIRI().getFragment().isEmpty()) {
                result = clazz.getIRI().getFragment();
            } else {
                result = clazz.getIRI().toString();
            }
        }
        if (replaceUnderscore) {
            result = result.replaceAll("_", " ");
        }
        return result;
    }
}