package it.unitn.disi.smatch.gui;

import com.ikayzo.swing.icon.IconUtils;
import com.ikayzo.swing.icon.JIconFile;
import com.ikayzo.swing.icon.LayeredIcon;
import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import it.unitn.disi.smatch.*;
import it.unitn.disi.smatch.async.AsyncTask;
import it.unitn.disi.smatch.data.mappings.IContextMapping;
import it.unitn.disi.smatch.data.mappings.IMappingElement;
import it.unitn.disi.smatch.data.mappings.MappingElement;
import it.unitn.disi.smatch.data.trees.IBaseNode;
import it.unitn.disi.smatch.data.trees.IContext;
import it.unitn.disi.smatch.data.trees.INode;
import it.unitn.disi.smatch.loaders.context.IAsyncContextLoader;
import it.unitn.disi.smatch.loaders.mapping.IAsyncMappingLoader;
import it.unitn.disi.smatch.renderers.context.IAsyncContextRenderer;
import it.unitn.disi.smatch.renderers.mapping.IAsyncMappingRenderer;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/**
 * GUI for S-Match.
 *
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class SMatchGUI extends Observable implements Observer, Executor {

    private static final Logger log;

    static {
        String log4jConf = System.getProperty("log4j.configuration");
        if (null != log4jConf) {
            PropertyConfigurator.configure(log4jConf);
        } else {
            PropertyConfigurator.configure(".." + File.separator + "conf" + File.separator + "log4j-gui.properties");
        }

        // c3p0 lib used by hibernate in Diversicon is way too chatty, and I couldn't 
        // silence it with log4j properties nor it picked the  
        // file mchange-log.properties I usually put in resources
        System.setProperty("com.mchange.v2.log.MLog", "com.mchange.v2.log.FallbackMLog");
        System.setProperty("com.mchange.v2.log.FallbackMLog.DEFAULT_CUTOFF_LEVEL", "OFF"); // Off or any other level
        
        log = Logger.getLogger(SMatchGUI.class);
    }

    private static final String MAIN_ICON_FILE = "/it/unitn/disi/smatch/gui/s-match.ico";
    private static final String GUI_CONF_FILE = ".." + File.separator + "conf" + File.separator + "SMatchGUI.properties";
    private Properties properties;

    private String lookAndFeel = null;

    private volatile IMatchManager mm = null;
    private IContext source = null;
    private boolean sourceModified = false;
    private String sourceLocation = null;
    private IContext target = null;
    private boolean targetModified = false;
    private String targetLocation = null;
    private IContextMapping<INode> mapping = null;
    private boolean mappingModified = false;
    private String mappingLocation = null;
    private JTree lastFocusedTree;
    private int cbConfigPrevIndex;

    private final int PERMITS = Integer.MAX_VALUE;
    private final Semaphore semMapping = new Semaphore(PERMITS);
    private final Semaphore semSource = new Semaphore(PERMITS);
    private final Semaphore semTarget = new Semaphore(PERMITS);
    private final Semaphore semManager = new Semaphore(PERMITS);

    private String configFileName;

    // GUI static elements
    private JFrame frame;
    private JPanel mainPanel;
    private JMenuBar mainMenu;
    private JTextArea taLog;
    private DefaultComboBoxModel<String> cmConfigs;
    private JComboBox<String> cbConfig;
    private JFileChooser fc;
    private JTree tSource;
    private JTree tTarget;
    private JTable tblMapping;
    private JSplitPane spnContexts;
    private JSplitPane spnContextsMapping;
    private JPanel pnContexts;
    private JPanel pnContextsMapping;
    private JScrollPane spSource;
    private JScrollPane spTarget;
    private JSplitPane spnContextsLog;
    private JScrollPane spLog;
    private JPopupMenu popSource;
    private JPopupMenu popTarget;
    private JTextField teMappingLocation;
    private JTextField teSourceContextLocation;
    private JTextField teTargetContextLocation;
    private JProgressBar pbProgress;
    private JProgressBar pbSourceProgress;
    private JProgressBar pbTargetProgress;

    // actions
    private Action acSourceCreate;
    private Action acSourceAddNode;
    private Action acSourceAddChildNode;
    private Action acSourceDelete;
    private Action acSourceUncoalesce;
    private Action acSourceUncoalesceAll;
    private Action acSourceOpen;
    private Action acSourcePreprocess;
    private Action acSourceClose;
    private Action acSourceSave;
    private Action acSourceSaveAs;

    private Action acTargetCreate;
    private Action acTargetAddNode;
    private Action acTargetAddChildNode;
    private Action acTargetDelete;
    private Action acTargetUncoalesce;
    private Action acTargetUncoalesceAll;
    private Action acTargetOpen;
    private Action acTargetPreprocess;
    private Action acTargetClose;
    private Action acTargetSave;
    private Action acTargetSaveAs;

    private Action acMappingCreate;
    private Action acMappingOpen;
    private Action acMappingClose;
    private Action acMappingSave;
    private Action acMappingSaveAs;

    private Action acEditAddNode;
    private Action acEditAddChildNode;
    private Action acEditAddLink;
    private Action acEditDelete;

    private Action acViewUncoalesce;
    private Action acViewUncoalesceAll;

    private Action acConfigurationEdit;

    public static final String TANGO_ICONS_PATH = "/it/unitn/disi/smatch/gui/tango-icon-theme-0.8.90/";

    public static JIconFile loadIconFile(String name) {
        JIconFile icon = null;
        try {
            log.debug("Loading icon " + name);
            icon = new JIconFile(SMatchGUI.class.getResource(name + ".jic"));
        } catch (IOException e) {
            if (log.isEnabledFor(Level.ERROR)) {
                log.error("Error loading icon " + name, e);
            }
        }
        return icon;
    }

    private static final Icon documentOpenSmall;
    private static final Icon documentOpenLarge;
    private static final Icon documentSaveSmall;
    private static final Icon documentSaveLarge;
    private static final Icon documentSaveAsSmall;
    private static final Icon documentSaveAsLarge;
    private static final Icon folderSmall;
    private static final Icon folderOpenSmall;
    private static final Icon iconDJ;
    private static final Icon iconEQ;
    private static final Icon iconMG;
    private static final Icon iconLG;
    private static final Icon smallIconDJ;
    private static final Icon smallIconEQ;
    private static final Icon smallIconMG;
    private static final Icon smallIconLG;
    private static final Icon iconAddNodeLarge;
    private static Icon iconAddChildNodeLarge;
    private static Icon iconAddLinkLarge;
    private static final Icon iconDeleteLarge;
    private static final Icon iconAddNodeSmall;
    private static Icon iconAddChildNodeSmall;
    private static Icon iconAddLinkSmall;
    private static final Icon iconDeleteSmall;
    private static final Icon iconContextCreateLarge;
    private static final Icon iconContextCreateSmall;
    private static final Icon iconUncoalesceSmall;
    private static final Icon iconUncoalesceLarge;

    public static final int VERY_SMALL_ICON_SIZE = 12;
    public static final int SMALL_ICON_SIZE = 16;
    public static final int LARGE_ICON_SIZE = 32;

    private static final String EMPTY_ROOT_NODE_LABEL = "Create or open context";
    private static final String LOADING_LABEL = "Loading...";
    public static final String ELLIPSIS = "...";


    private static final String NAME_EQ = "equivalent";
    private static final String NAME_LG = "less general";
    private static final String NAME_MG = "more general";
    private static final String NAME_DJ = "disjoint";

    private static final String[] relStrings = {NAME_EQ, NAME_LG, NAME_MG, NAME_DJ};

    private static final HashMap<Character, String> relationToDescription = new HashMap<>(4);
    private static final HashMap<String, Character> descriptionToRelation = new HashMap<>(4);
    private static final HashMap<String, Icon> descriptionToIcon = new HashMap<>(4);

    static {
        JIconFile icon = loadIconFile(TANGO_ICONS_PATH + "actions/document-open");
        documentOpenSmall = icon.getIcon(SMALL_ICON_SIZE);
        documentOpenLarge = icon.getIcon(LARGE_ICON_SIZE);

        icon = loadIconFile(TANGO_ICONS_PATH + "actions/document-save");
        documentSaveSmall = icon.getIcon(SMALL_ICON_SIZE);
        documentSaveLarge = icon.getIcon(LARGE_ICON_SIZE);

        icon = loadIconFile(TANGO_ICONS_PATH + "actions/document-save-as");
        documentSaveAsSmall = icon.getIcon(SMALL_ICON_SIZE);
        documentSaveAsLarge = icon.getIcon(LARGE_ICON_SIZE);

        icon = loadIconFile(TANGO_ICONS_PATH + "places/folder");
        folderSmall = icon.getIcon(SMALL_ICON_SIZE);

        icon = loadIconFile(TANGO_ICONS_PATH + "status/folder-open");
        folderOpenSmall = icon.getIcon(SMALL_ICON_SIZE);

        icon = loadIconFile("/it/unitn/disi/smatch/gui/relations/disjoint");
        iconDJ = icon.getIcon(SMALL_ICON_SIZE);
        smallIconDJ = icon.getIcon(VERY_SMALL_ICON_SIZE);

        icon = loadIconFile("/it/unitn/disi/smatch/gui/relations/equivalent");
        iconEQ = icon.getIcon(SMALL_ICON_SIZE);
        smallIconEQ = icon.getIcon(VERY_SMALL_ICON_SIZE);

        icon = loadIconFile("/it/unitn/disi/smatch/gui/relations/less-general");
        iconLG = icon.getIcon(SMALL_ICON_SIZE);
        smallIconLG = icon.getIcon(VERY_SMALL_ICON_SIZE);

        icon = loadIconFile("/it/unitn/disi/smatch/gui/relations/more-general");
        iconMG = icon.getIcon(SMALL_ICON_SIZE);
        smallIconMG = icon.getIcon(VERY_SMALL_ICON_SIZE);

        icon = loadIconFile(TANGO_ICONS_PATH + "actions/go-jump");
        ImageIcon iconGoJumpLarge = icon.getIcon(LARGE_ICON_SIZE);
        ImageIcon iconGoJumpSmall = icon.getIcon(SMALL_ICON_SIZE);

        icon = loadIconFile(TANGO_ICONS_PATH + "actions/list-add");
        ImageIcon iconListAddLarge = icon.getIcon(LARGE_ICON_SIZE);
        ImageIcon iconListAddSmall = icon.getIcon(SMALL_ICON_SIZE);
        ImageIcon iconListAddSmallest = icon.getIcon(SMALL_ICON_SIZE / 2);

        iconAddLinkLarge = new LayeredIcon(iconGoJumpLarge, iconListAddSmall, SwingConstants.RIGHT, SwingConstants.BOTTOM, 4, 4);
        iconAddLinkSmall = new LayeredIcon(iconGoJumpSmall, iconListAddSmallest, SwingConstants.RIGHT, SwingConstants.BOTTOM, 2, 2);

        // convert to ImageIcon, otherwise disabled icon does not render due to restriction in LookAndFeel.java
        iconAddLinkLarge = IconUtils.makeIconFromComponent(new JLabel(iconAddLinkLarge), LARGE_ICON_SIZE, LARGE_ICON_SIZE, true);
        iconAddLinkSmall = IconUtils.makeIconFromComponent(new JLabel(iconAddLinkSmall), SMALL_ICON_SIZE, SMALL_ICON_SIZE, true);

        iconAddNodeSmall = iconListAddSmall;
        iconAddNodeLarge = iconListAddLarge;

        icon = loadIconFile(TANGO_ICONS_PATH + "actions/list-remove");
        iconDeleteSmall = icon.getIcon(SMALL_ICON_SIZE);
        iconDeleteLarge = icon.getIcon(LARGE_ICON_SIZE);

        icon = loadIconFile(TANGO_ICONS_PATH + "actions/document-new");
        iconContextCreateSmall = icon.getIcon(SMALL_ICON_SIZE);
        iconContextCreateLarge = icon.getIcon(LARGE_ICON_SIZE);

        iconAddChildNodeLarge = new LayeredIcon(iconListAddLarge, iconListAddSmall, SwingConstants.RIGHT, SwingConstants.BOTTOM, 4, 4);
        iconAddChildNodeSmall = new LayeredIcon(iconListAddSmall, iconListAddSmallest, SwingConstants.RIGHT, SwingConstants.BOTTOM, 2, 2);

        // convert to ImageIcon, otherwise disabled icon does not render due to restriction in LookAndFeel.java
        iconAddChildNodeLarge = IconUtils.makeIconFromComponent(new JLabel(iconAddChildNodeLarge), LARGE_ICON_SIZE, LARGE_ICON_SIZE, true);
        iconAddChildNodeSmall = IconUtils.makeIconFromComponent(new JLabel(iconAddChildNodeSmall), SMALL_ICON_SIZE, SMALL_ICON_SIZE, true);

        icon = loadIconFile(TANGO_ICONS_PATH + "actions/view-fullscreen");
        iconUncoalesceSmall = icon.getIcon(SMALL_ICON_SIZE);
        iconUncoalesceLarge = icon.getIcon(LARGE_ICON_SIZE);

        relationToDescription.put(IMappingElement.EQUIVALENCE, NAME_EQ);
        relationToDescription.put(IMappingElement.LESS_GENERAL, NAME_LG);
        relationToDescription.put(IMappingElement.MORE_GENERAL, NAME_MG);
        relationToDescription.put(IMappingElement.DISJOINT, NAME_DJ);

        descriptionToRelation.put(NAME_EQ, IMappingElement.EQUIVALENCE);
        descriptionToRelation.put(NAME_LG, IMappingElement.LESS_GENERAL);
        descriptionToRelation.put(NAME_MG, IMappingElement.MORE_GENERAL);
        descriptionToRelation.put(NAME_DJ, IMappingElement.DISJOINT);

        descriptionToIcon.put(NAME_EQ, smallIconEQ);
        descriptionToIcon.put(NAME_LG, smallIconLG);
        descriptionToIcon.put(NAME_MG, smallIconMG);
        descriptionToIcon.put(NAME_DJ, smallIconDJ);
    }

    /**
     * A tree model that includes the mapping. Supports coalescing nodes.
     * There can be one range of coalesced nodes among node's children. For example, let these be some node's children
     * 111
     * 222
     * 333
     * 444
     * <p/>
     * if children 1->3 became coalesced
     * <p/>
     * ...  -> 111,222,333
     * 444
     * <p/>
     * Coalesce operation hides the nodes by not reporting them to the tree.
     *
     * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
     */
    public static class BaseCoalesceTreeModel extends DefaultTreeModel {

        protected final IBaseNode root;

        public class Coalesce {
            public final Point range;
            public final DefaultMutableTreeNode sub;
            public final IBaseNode parent;

            private Coalesce(Point range, DefaultMutableTreeNode sub, IBaseNode parent) {
                this.range = range;
                this.sub = sub;
                this.parent = parent;
            }
        }

        // for each node keep an inclusive range of its coalesced children plus a substitute node with ellipsis
        protected final HashMap<IBaseNode, Coalesce> coalesce = new HashMap<>();

        public BaseCoalesceTreeModel(IBaseNode root) {
            super(root);
            this.root = root;
        }

        /**
         * Coalesces the <code>parent</code>'s children from <code>start</code> to <code>end</code> (inclusive).
         *
         * @param parent the node with children to coalesce
         * @param start  starting index
         * @param end    ending index
         */
        public void coalesce(IBaseNode parent, int start, int end) {
            Coalesce c = coalesce.get(parent);
            if (null != c) {
                uncoalesce(parent);
            }

            if (0 <= start && end < getChildCount(parent) && start < end) {
                DefaultMutableTreeNode dmtn = new DefaultMutableTreeNode();
                c = new Coalesce(new Point(start, end), dmtn, parent);
                dmtn.setUserObject(c);
                coalesce.put(parent, c);

                int[] childIndices = new int[end - start + 1];
                Object[] removedChildren = new Object[end - start + 1];
                for (int i = 0; i < childIndices.length; i++) {
                    childIndices[i] = start + i;
                    if ((start + i) < parent.getChildCount()) {
                        removedChildren[i] = parent.getChildAt(start + i);
                    }
                }
                //signal the "removal" of a range
                nodesWereRemoved(parent, childIndices, removedChildren);
                //signal the insertion of a sub
                nodesWereInserted(parent, new int[]{start});
            }
        }

        /**
         * Expands coalesced children.
         *
         * @param parent node to expand coalesced children.
         */
        public void uncoalesce(IBaseNode parent) {
            Coalesce c = coalesce.get(parent);
            if (null != c) {
                coalesce.remove(parent);

                int[] childIndices = new int[c.range.y - c.range.x + 1];
                for (int i = 0; i < childIndices.length; i++) {
                    childIndices[i] = c.range.x + i;
                }
                //signal the deletion of a sub
                nodesWereRemoved(parent, new int[]{c.range.x}, new Object[]{c.sub});
                //signal the "insertion" of a range
                nodesWereInserted(parent, childIndices);
            }
        }

        /**
         * Expands all coalesced nodes.
         */
        public void uncoalesceAll() {
            List<IBaseNode> parents = new ArrayList<>(coalesce.keySet());
            while (0 < parents.size()) {
                uncoalesce(parents.get(0));
                parents.remove(0);
            }
            coalesce.clear();
        }


        /**
         * Expands coalesced children in parent nodes until the node becomes visible.
         *
         * @param node to make visible
         */
        public void uncoalesceParents(final IBaseNode node) {
            IBaseNode curNode = node;
            while (null != curNode && isCoalescedInAnyParent(curNode)) {
                if (isCoalesced(curNode)) {
                    uncoalesce(curNode.getParent());
                }
                curNode = curNode.getParent();
            }
        }


        /**
         * Returns whether the <code>node</code> is coalesced.
         *
         * @param node node to check
         * @return whether the node is coalesced
         */
        @SuppressWarnings("unchecked")
        public boolean isCoalesced(IBaseNode node) {
            boolean result = false;
            IBaseNode parent = node.getParent();
            if (null != parent) {
                Coalesce c = coalesce.get(parent);
                if (null != c) {
                    int idx = parent.getChildIndex(node);
                    result = c.range.x <= idx && idx <= c.range.y;
                }
            }
            return result;
        }

        /**
         * Returns whether any of the <code>node</code>'s parents is coalesced.
         *
         * @param node node to check
         * @return whether any of the node's parents is coalesced
         */
        public boolean isCoalescedInAnyParent(IBaseNode node) {
            boolean result = false;
            IBaseNode curNode = node;
            while (null != curNode && !result) {
                result = isCoalesced(curNode);
                curNode = curNode.getParent();
            }
            return result;
        }

        /**
         * Returns whether there is a coalesced node in this model.
         *
         * @return whether there is a coalesced node in this model
         */
        public boolean hasCoalescedNode() {
            return !coalesce.isEmpty();
        }

        @Override
        public Object getChild(Object parent, int index) {
            Object result = null;
            if (parent instanceof IBaseNode) {
                IBaseNode parentNode = (IBaseNode) parent;
                Coalesce c = coalesce.get(parentNode);
                if (null == c) {
                    if (0 <= index && index < parentNode.getChildCount()) {
                        result = parentNode.getChildAt(index);
                    }
                } else {
                    @SuppressWarnings("unchecked")
                    final int coalescedLength = c.range.y - c.range.x;
                    final int coalescedIdx = parentNode.getChildCount() - coalescedLength;
                    if (0 <= index && index < coalescedIdx) {
                        if (index == c.range.x) {
                            result = c.sub;
                        } else {
                            if (index < c.range.x) {
                                if (index < parentNode.getChildCount()) {
                                    result = parentNode.getChildAt(index);
                                }
                            } else {
                                //index > c.range.x
                                //result = parentNode.getChildAt(index + coalescedLength);
                                if ((index + coalescedLength) < parentNode.getChildCount()) {
                                    result = parentNode.getChildAt(index + coalescedLength);
                                }
                            }
                        }
                    }
                }
            }
            return result;
        }

        @Override
        public int getChildCount(Object parent) {
            int result = 0;
            if (parent instanceof IBaseNode) {
                IBaseNode parentNode = (IBaseNode) parent;
                Coalesce c = coalesce.get(parentNode);
                if (null == c) {
                    result = parentNode.getChildCount();
                } else {
                    final int coalescedLength = c.range.y - c.range.x;
                    result = parentNode.getChildCount() - coalescedLength;
                }
            }
            return result;
        }

        @Override
        public boolean isLeaf(Object node) {
            boolean result = true;
            if (node instanceof IBaseNode) {
                IBaseNode iNode = (IBaseNode) node;
                result = 0 == iNode.getChildCount();
            }
            return result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public int getIndexOfChild(Object parent, Object child) {
            int result = -1;
            if (null != parent && null != child) {
                if (parent instanceof IBaseNode) {
                    IBaseNode pNode = (IBaseNode) parent;
                    Coalesce c = coalesce.get(pNode);
                    if (null == c) {
                        if (child instanceof IBaseNode) {
                            IBaseNode cNode = (IBaseNode) child;
                            result = pNode.getChildIndex(cNode);
                        } else {
                            if (child instanceof DefaultMutableTreeNode) {
                                result = pNode.getChildCount();
                            }
                        }
                    } else {
                        final int coalescedLength = c.range.y - c.range.x;
                        if (child instanceof IBaseNode) {
                            IBaseNode cNode = (IBaseNode) child;
                            result = pNode.getChildIndex(cNode);
                            if (c.range.x < result && result < c.range.y) {
                                result = -1;//the node is coalesced?
                            } else {
                                if (c.range.y < result) {
                                    result = result - coalescedLength;
                                }
                            }
                        } else {
                            if (child instanceof DefaultMutableTreeNode) {
                                DefaultMutableTreeNode dmtn = (DefaultMutableTreeNode) child;
                                if (dmtn.getUserObject() instanceof String) {
                                    //sub node
                                    result = c.range.x;
                                }
                            }
                        }
                    }
                }
            }
            return result;
        }

        @Override
        public void nodesWereInserted(TreeNode node, int[] childIndices) {
            if (listenerList != null && node != null && childIndices != null && childIndices.length > 0) {
                int cCount = childIndices.length;
                Object[] newChildren = new Object[cCount];

                for (int counter = 0; counter < cCount; counter++) {
                    newChildren[counter] = getChild(node, childIndices[counter]);
                }
                fireTreeNodesInserted(this, getPathToRoot(node), childIndices, newChildren);
            }
        }

        @Override
        public void nodesChanged(TreeNode node, int[] childIndices) {
            if (node != null) {
                if (childIndices != null) {
                    int cCount = childIndices.length;

                    if (cCount > 0) {
                        Object[] cChildren = new Object[cCount];

                        for (int counter = 0; counter < cCount; counter++) {
                            cChildren[counter] = getChild(node, childIndices[counter]);
                        }
                        fireTreeNodesChanged(this, getPathToRoot(node), childIndices, cChildren);
                    }
                } else if (node == getRoot()) {
                    fireTreeNodesChanged(this, getPathToRoot(node), null, null);
                }
            }
        }
    }

    private class MappingTreeModel extends BaseCoalesceTreeModel {

        //whether this tree is a source tree of a mapping
        protected final boolean isSource;

        protected IContextMapping<INode> mapping;

        public MappingTreeModel(IBaseNode root, boolean isSource, IContextMapping<INode> mapping) {
            super(root);
            this.isSource = isSource;
            this.mapping = mapping;
        }

        public void setMapping(IContextMapping<INode> mapping) {
            this.mapping = mapping;
        }

        /**
         * Coalesces the <code>parent</code>'s children from <code>start</code> to <code>end</code> (inclusive).
         *
         * @param bParent the node with children to coalesce
         * @param start   starting index
         * @param end     ending index
         */
        public void coalesce(IBaseNode bParent, int start, int end) {
            INode parent = (INode) bParent;
            Coalesce c = coalesce.get(parent);
            if (null != c) {
                uncoalesce(parent);
            }
            @SuppressWarnings("unchecked")
            List<DefaultMutableTreeNode> linkNodes = (List<DefaultMutableTreeNode>) parent.nodeData().getUserObject();
            if (null == linkNodes) {
                linkNodes = updateUserObject(parent);
            }

            if (0 <= start && end < getChildCount(parent) && start < end) {
                DefaultMutableTreeNode dmtn = new DefaultMutableTreeNode();
                c = new Coalesce(new Point(start, end), dmtn, parent);
                dmtn.setUserObject(c);
                coalesce.put(parent, c);

                int[] childIndices = new int[end - start + 1];
                Object[] removedChildren = new Object[end - start + 1];
                for (int i = 0; i < childIndices.length; i++) {
                    childIndices[i] = start + i;
                    if ((start + i) < parent.getChildCount()) {
                        removedChildren[i] = parent.getChildAt(start + i);
                    } else {
                        removedChildren[i] = linkNodes.get(start + i - parent.getChildCount());
                    }

                }
                //signal the "removal" of a range
                nodesWereRemoved(parent, childIndices, removedChildren);
                //signal the insertion of a sub
                nodesWereInserted(parent, new int[]{start});
            }
        }

        @Override
        public Object getRoot() {
            if (null != root && null == root.nodeData().getUserObject()) {
                updateUserObject(root);
            }

            return root;
        }

        public List<DefaultMutableTreeNode> updateUserObject(final IBaseNode bNode) {
            INode node = (INode) bNode;
            List<DefaultMutableTreeNode> result = Collections.emptyList();
            Set<IMappingElement<INode>> links;
            if (null != mapping) {
                if (isSource) {
                    links = mapping.getSources(node);
                } else {
                    links = mapping.getTargets(node);
                }
                result = new ArrayList<>();
                for (IMappingElement<INode> me : links) {
                    result.add(new DefaultMutableTreeNode(me));
                }
            }
            node.nodeData().setUserObject(result);
            return result;
        }

        @Override
        public Object getChild(Object parent, int index) {
            Object result = null;
            if (parent instanceof INode) {
                INode parentNode = (INode) parent;
                Coalesce c = coalesce.get(parentNode);
                if (null == c) {
                    if (0 <= index && index < parentNode.getChildCount()) {
                        result = parentNode.getChildAt(index);
                    } else {
                        @SuppressWarnings("unchecked")
                        List<DefaultMutableTreeNode> linkNodes = (List<DefaultMutableTreeNode>) parentNode.nodeData().getUserObject();
                        if (null == linkNodes) {
                            linkNodes = updateUserObject(parentNode);
                        }
                        if (parentNode.getChildCount() <= index && index < (parentNode.getChildCount() + linkNodes.size())) {
                            result = linkNodes.get(index - parentNode.getChildCount());
                        }
                    }
                } else {
                    @SuppressWarnings("unchecked")
                    List<DefaultMutableTreeNode> linkNodes = (List<DefaultMutableTreeNode>) parentNode.nodeData().getUserObject();
                    if (null == linkNodes) {
                        linkNodes = updateUserObject(parentNode);
                    }
                    final int coalescedLength = c.range.y - c.range.x;
                    final int coalescedIdx = parentNode.getChildCount() + linkNodes.size() - coalescedLength;
                    if (0 <= index && index < coalescedIdx) {
                        if (index == c.range.x) {
                            result = c.sub;
                        } else {
                            if (index < c.range.x) {
                                if (index < parentNode.getChildCount()) {
                                    result = parentNode.getChildAt(index);
                                } else {
                                    result = linkNodes.get(index - parentNode.getChildCount());
                                }
                            } else {
                                //index > c.range.x
                                //result = parentNode.getChildAt(index + coalescedLength);
                                if ((index + coalescedLength) < parentNode.getChildCount()) {
                                    result = parentNode.getChildAt(index + coalescedLength);
                                } else {
                                    result = linkNodes.get(index - parentNode.getChildCount() + coalescedLength);
                                }
                            }
                        }
                    }
                }
            }
            return result;
        }

        @Override
        public int getChildCount(Object parent) {
            int result = 0;
            if (parent instanceof INode) {
                INode parentNode = (INode) parent;
                @SuppressWarnings("unchecked")
                List<DefaultMutableTreeNode> linkNodes = (List<DefaultMutableTreeNode>) parentNode.nodeData().getUserObject();
                if (null == linkNodes) {
                    linkNodes = updateUserObject(parentNode);
                }
                Coalesce c = coalesce.get(parentNode);
                if (null == c) {
                    result = parentNode.getChildCount() + linkNodes.size();
                } else {
                    final int coalescedLength = c.range.y - c.range.x;
                    result = parentNode.getChildCount() + linkNodes.size() - coalescedLength;
                }
            }
            return result;
        }

        @Override
        public boolean isLeaf(Object node) {
            boolean result = true;
            if (node instanceof INode) {
                INode iNode = (INode) node;
                @SuppressWarnings("unchecked")
                List<DefaultMutableTreeNode> linkNodes = (List<DefaultMutableTreeNode>) iNode.nodeData().getUserObject();
                if (null == linkNodes) {
                    linkNodes = updateUserObject(iNode);
                }

                result = 0 == iNode.getChildCount() && 0 == linkNodes.size();
            }
            return result;
        }

        @Override
        public void valueForPathChanged(TreePath path, Object newValue) {
            Object o = path.getLastPathComponent();
            if (o instanceof INode) {
                INode node = (INode) o;
                if (newValue instanceof String) {
                    String text = (String) newValue;
                    if (!node.nodeData().getName().equals(text)) {
                        node.nodeData().setName(text);
                        node.nodeData().setIsPreprocessed(false);
                        if (isSource) {
                            sourceModified = true;
                        } else {
                            targetModified = true;
                        }
                        //notify mapping table
                        if (null != mapping && tblMapping.getModel() instanceof MappingTableModel) {
                            MappingTableModel mtm = (MappingTableModel) tblMapping.getModel();
                            Set<IMappingElement<INode>> links;
                            if (isSource) {
                                links = mapping.getSources(node);
                            } else {
                                links = mapping.getTargets(node);
                            }
                            for (IMappingElement<INode> me : links) {
                                mtm.fireElementChanged(me);
                            }
                        }
                        setChanged();
                        notifyObservers();
                    }
                }
            } else if (o instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode dmtn = (DefaultMutableTreeNode) o;
                if (newValue instanceof Character && dmtn.getUserObject() instanceof IMappingElement) {
                    Character rel = (Character) newValue;
                    @SuppressWarnings("unchecked")
                    IMappingElement<INode> me = (IMappingElement<INode>) dmtn.getUserObject();
                    mapping.setRelation(me.getSource(), me.getTarget(), rel);
                    mappingModified = true;
                    //notify mapping table
                    if (tblMapping.getModel() instanceof MappingTableModel) {
                        MappingTableModel mtm = (MappingTableModel) tblMapping.getModel();
                        mtm.fireElementChanged(me);
                    }
                    setChanged();
                    notifyObservers();
                }
            }
        }

        @Override
        public int getIndexOfChild(Object parent, Object child) {
            int result = -1;
            if (null != parent && null != child) {
                if (parent instanceof INode) {
                    INode pNode = (INode) parent;
                    Coalesce c = coalesce.get(pNode);
                    if (null == c) {
                        if (child instanceof INode) {
                            INode cNode = (INode) child;
                            result = pNode.getChildIndex(cNode);
                        } else {
                            if (child instanceof DefaultMutableTreeNode) {
                                DefaultMutableTreeNode dmtn = (DefaultMutableTreeNode) child;
                                @SuppressWarnings("unchecked")
                                List<DefaultMutableTreeNode> linkNodes = (List<DefaultMutableTreeNode>) pNode.nodeData().getUserObject();
                                if (null == linkNodes) {
                                    linkNodes = updateUserObject(pNode);
                                }
                                result = pNode.getChildCount() + linkNodes.indexOf(dmtn);
                            }
                        }
                    } else {
                        final int coalescedLength = c.range.y - c.range.x;
                        if (child instanceof INode) {
                            INode cNode = (INode) child;
                            result = pNode.getChildIndex(cNode);
                            if (c.range.x < result && result < c.range.y) {
                                result = -1;//the node is coalesced? 
                            } else {
                                if (c.range.y < result) {
                                    result = result - coalescedLength;
                                }
                            }
                        } else {
                            if (child instanceof DefaultMutableTreeNode) {
                                DefaultMutableTreeNode dmtn = (DefaultMutableTreeNode) child;
                                if (dmtn.getUserObject() instanceof String) {
                                    //sub node
                                    result = c.range.x;
                                } else {
                                    @SuppressWarnings("unchecked")
                                    List<DefaultMutableTreeNode> linkNodes = (List<DefaultMutableTreeNode>) pNode.nodeData().getUserObject();
                                    if (null == linkNodes) {
                                        linkNodes = updateUserObject(pNode);
                                    }
                                    result = linkNodes.indexOf(dmtn);
                                    if (-1 < result) {
                                        result = pNode.getChildCount() + result;
                                        if (c.range.x < result && result < c.range.y) {
                                            result = -1;//coalesced?
                                        } else {
                                            if (c.range.y < result) {
                                                result = result - coalescedLength;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return result;
        }
    }

    private class MappingTableModel extends AbstractTableModel {

        private final IContextMapping<INode> mapping;
        private HashMap<Integer, IMappingElement<INode>> order;
        private HashMap<IMappingElement<INode>, Integer> backOrder;

        public final static int C_SOURCE = 0;
        public final static int C_RELATION = 1;
        public final static int C_TARGET = 2;

        private final String[] columnNames = {"Source", "Relation", "Target"};

        private MappingTableModel(IContextMapping<INode> mapping) {
            this.mapping = mapping;
            if (null != mapping) {
                imposeOrder(mapping);

            }
        }

        private void imposeOrder(IContextMapping<INode> mapping) {
            order = new HashMap<>(mapping.size());
            backOrder = new HashMap<>(mapping.size());
            int i = 0;
            Iterator<INode> iterator = mapping.getSourceContext().nodeIterator();
            while (iterator.hasNext()) {
                INode source = iterator.next();
                for (IMappingElement<INode> e : mapping.getSources(source)) {
                    order.put(i, e);
                    backOrder.put(e, i);
                    i++;
                }
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return IMappingElement.class;
        }

        public String getColumnName(int col) {
            return columnNames[col];
        }

        public int getRowCount() {
            if (null != mapping) {
                return mapping.size();
            } else {
                return 0;
            }
        }

        public int getColumnCount() {
            return columnNames.length;
        }

        public IMappingElement<INode> getElementAt(int row) {
            IMappingElement<INode> result = null;
            if (null != mapping) {
                result = order.get(row);
            }
            return result;
        }

        public int getIndexOf(IMappingElement<INode> me) {
            Integer result = null;
            if (null != mapping) {
                result = backOrder.get(me);
            }
            if (null != result) {
                return result;
            } else {
                return -1;
            }
        }


        public Object getValueAt(int row, int col) {
            return order.get(row);
        }

        public boolean isCellEditable(int row, int col) {
            return C_RELATION == col;
        }

        public void setValueAt(Object value, int row, int col) {
            super.fireTableCellUpdated(row, col);
        }

        public void fireElementInserted(IMappingElement<INode> me) {
            final int idx = getRowCount() - 1;
            order.put(idx, me);
            backOrder.put(me, idx);
            super.fireTableRowsInserted(idx, idx);
        }

        public void fireElementRemoved(IMappingElement<INode> me) {
            Integer idx = backOrder.get(me);
            if (null != idx) {
                backOrder.remove(me);
                order.remove(idx);
                order.remove(getRowCount() + 1);
                //decrease all the row numbers following the deleted one
                for (Map.Entry<IMappingElement<INode>, Integer> e : backOrder.entrySet()) {
                    if (idx < e.getValue()) {
                        e.setValue(e.getValue() - 1);
                    }
                    order.put(e.getValue(), e.getKey());
                }
                super.fireTableRowsDeleted(idx, idx);
            }
        }

        public void fireElementChanged(IMappingElement<INode> me) {
            Integer idx = backOrder.get(me);
            if (null != idx) {
                super.fireTableRowsUpdated(idx, idx);
            }
        }
    }

    private class ActionConfigurationEdit extends AbstractAction implements Observer {
        public ActionConfigurationEdit() {
            super("Edit configuration...");
            putValue(Action.SHORT_DESCRIPTION, "Edit configuration file");
            putValue(Action.LONG_DESCRIPTION, "Edit current configuration file");
        }

        public void actionPerformed(ActionEvent actionEvent) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop desktop = Desktop.getDesktop();
                    final File fileToEdit = new File(configFileName);
                    desktop.edit(fileToEdit.getCanonicalFile());
                } else {
                    JOptionPane.showMessageDialog(frame, "This Desktop environment is not supported by the Java machine.", "Desktop not supported", JOptionPane.WARNING_MESSAGE);
                }

                setChanged();
                notifyObservers();
            } catch (IOException e) {
                if (log.isEnabledFor(Level.ERROR)) {
                    log.error("Error launching editor for configuration file " + configFileName, e);
                }
                JOptionPane.showMessageDialog(frame, "Error launching editor for configuration file " + configFileName + ".\n\n" +
                                e.getMessage() + "\nPlease edit the file " + configFileName + " using your preferred text editor.",
                        "Configuration editing error", JOptionPane.ERROR_MESSAGE);
            }
        }

        public void update(Observable o, Object arg) {
            setEnabled(null != configFileName);
        }
    }

    private class ActionBrowseURL extends AbstractAction {

        private final String url;

        private ActionBrowseURL(String url, String name) {
            super(name);
            this.url = url;
        }

        public void actionPerformed(ActionEvent actionEvent) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop desktop = Desktop.getDesktop();
                    desktop.browse(new URI(url));
                } else {
                    JOptionPane.showMessageDialog(frame, "This Desktop environment is not supported by the Java machine.", "Desktop not supported", JOptionPane.WARNING_MESSAGE);
                }
            } catch (IOException | URISyntaxException e) {
                if (log.isEnabledFor(Level.ERROR)) {
                    log.error("Error while launching a browser at " + url, e);
                }
                JOptionPane.showMessageDialog(frame, "Error while launching a browser at " + url + ".\n\n" +
                                e.getMessage() + "\nPlease open a browser at " + url,
                        "Browser launch error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private class ActionSourceCreate extends AbstractAction implements Observer {
        public ActionSourceCreate() {
            super("Create");
            putValue(Action.SHORT_DESCRIPTION, "Creates Source");
            putValue(Action.LONG_DESCRIPTION, "Creates Source Context");
            putValue(Action.SMALL_ICON, iconContextCreateSmall);
            putValue(Action.LARGE_ICON_KEY, iconContextCreateLarge);
        }

        public void actionPerformed(ActionEvent actionEvent) {
            if (acMappingClose.isEnabled()) {
                acMappingClose.actionPerformed(actionEvent);
            }
            if (!acMappingClose.isEnabled()) {
                if (acSourceClose.isEnabled()) {
                    acSourceClose.actionPerformed(actionEvent);
                }
                if (!acSourceClose.isEnabled()) {
                    source = getMatchManager().createContext();
                    source.createRoot("Top");
                    if (null != target) {
                        mapping = getMatchManager().getMappingFactory().getContextMappingInstance(source, target);
                        resetMappingInModel(tTarget);
                        resetMappingInTable();
                    }
                    createTree(source, tSource, mapping);
                    sourceLocation = null;
                    sourceModified = false;
                }
            }
            setChanged();
            notifyObservers();
        }

        public void update(Observable o, Object arg) {
            setEnabled(null != getMatchManager() && PERMITS == semSource.availablePermits());
        }
    }

    private abstract class ActionTreeOpen extends AbstractAction implements Observer {

        protected ActionTreeOpen(String name) {
            super(name);
        }

        public void actionPerformed(ActionEvent actionEvent) {
            if (acMappingClose.isEnabled()) {
                acMappingClose.actionPerformed(actionEvent);
            }
            if (!acMappingClose.isEnabled()) {
                ff.setDescription(getMatchManager().getContextLoader().getDescription());
                getFileChooser().addChoosableFileFilter(ff);
                final int returnVal = getFileChooser().showOpenDialog(mainPanel);
                getFileChooser().removeChoosableFileFilter(ff);

                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    open(getFileChooser().getSelectedFile());
                }
            }
        }

        protected abstract void open(File file);

        public void update(Observable o, Object arg) {
            setEnabled(null != getMatchManager() && null != getMatchManager().getContextLoader());
        }
    }

    private class ActionSourceOpen extends ActionTreeOpen implements Observer {
        public ActionSourceOpen() {
            super("Open...");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_O));
            putValue(Action.SHORT_DESCRIPTION, "Opens Source");
            putValue(Action.LONG_DESCRIPTION, "Opens Source Context");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
            putValue(Action.SMALL_ICON, documentOpenSmall);
            putValue(Action.LARGE_ICON_KEY, documentOpenLarge);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (acSourceClose.isEnabled()) {
                acSourceClose.actionPerformed(actionEvent);
            }
            if (!acSourceClose.isEnabled()) {
                super.actionPerformed(actionEvent);
                sourceModified = false;
            }
            setChanged();
            notifyObservers();
        }

        @Override
        public void update(Observable o, Object arg) {
            super.update(o, arg);
            setEnabled(isEnabled() && PERMITS == semSource.availablePermits());
        }

        @Override
        protected void open(File file) {
            openSource(file);
        }
    }

    private class ActionSourcePreprocess extends AbstractAction implements Observer {
        public ActionSourcePreprocess() {
            super("Preprocess");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_P));
            putValue(Action.SHORT_DESCRIPTION, "Preprocesses Source");
            putValue(Action.LONG_DESCRIPTION, "Preprocesses Source Context");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK));
        }

        public void actionPerformed(ActionEvent actionEvent) {
            SwingWorker<Void, Void> offlineTask = createContextOfflineTask(source, semSource, pbSourceProgress, true);
            offlineTask.execute();
        }

        public void update(Observable o, Object arg) {
            setEnabled(null != getMatchManager() && null != source && null != getMatchManager().getContextPreprocessor()
                    && PERMITS == semSource.availablePermits());
        }
    }

    private class ActionSourceClose extends AbstractAction implements Observer {
        public ActionSourceClose() {
            super("Close");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_C));
            putValue(Action.SHORT_DESCRIPTION, "Closes Source");
            putValue(Action.LONG_DESCRIPTION, "Closes Source Context");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.CTRL_DOWN_MASK));
        }

        public void actionPerformed(ActionEvent actionEvent) {
            if (acMappingClose.isEnabled()) {
                acMappingClose.actionPerformed(actionEvent);
            }
            if (!acMappingClose.isEnabled()) {
                int choice = 1;//no, don't save.
                if (sourceModified) {
                    choice = JOptionPane.showOptionDialog(frame,
                            "The source context has been changed.\n\nSave the source context?",
                            "Save the source context?",
                            JOptionPane.YES_NO_CANCEL_OPTION,
                            JOptionPane.QUESTION_MESSAGE,
                            null,
                            null,
                            0);
                }
                switch (choice) {
                    case 0: {//yes, save
                        acSourceSave.actionPerformed(actionEvent);
                        if (!acSourceSave.isEnabled()) {
                            closeSource();
                        }
                        break;
                    }
                    case 1: {//no, don't save
                        closeSource();
                        break;
                    }
                    case 2: {//cancel
                        break;
                    }
                    default: {//cancel
                    }
                }
            }
        }

        public void update(Observable o, Object arg) {
            setEnabled(null != getMatchManager() && null != source && PERMITS == semSource.availablePermits());
        }
    }

    private class ActionSourceSave extends AbstractAction implements Observer {
        public ActionSourceSave() {
            super("Save");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_S));
            putValue(Action.SHORT_DESCRIPTION, "Saves Source");
            putValue(Action.LONG_DESCRIPTION, "Saves Source Context");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
            putValue(Action.SMALL_ICON, documentSaveSmall);
            putValue(Action.LARGE_ICON_KEY, documentSaveLarge);
        }

        public void actionPerformed(ActionEvent actionEvent) {
            if (null == sourceLocation) {
                ff.setDescription(getMatchManager().getContextRenderer().getDescription());
                getFileChooser().addChoosableFileFilter(ff);
                final int returnVal = getFileChooser().showSaveDialog(mainPanel);
                getFileChooser().removeChoosableFileFilter(ff);

                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    sourceLocation = getFileChooser().getSelectedFile().getAbsolutePath();
                }
            }

            if (null != sourceLocation) {
                saveSource(sourceLocation);
            }

            setChanged();
            notifyObservers();
        }

        public void update(Observable o, Object arg) {
            setEnabled(null != getMatchManager() && null != source && null != getMatchManager().getContextRenderer()
                    && sourceModified
                    // exclusivity because uses the same progress bar
                    && PERMITS == semSource.availablePermits());
        }
    }

    private class ActionSourceSaveAs extends AbstractAction implements Observer {
        public ActionSourceSaveAs() {
            super("Save As...");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_A));
            putValue(Action.SHORT_DESCRIPTION, "Saves Source");
            putValue(Action.LONG_DESCRIPTION, "Saves Source Context");
            putValue(Action.SMALL_ICON, documentSaveAsSmall);
            putValue(Action.LARGE_ICON_KEY, documentSaveAsLarge);
        }

        public void actionPerformed(ActionEvent actionEvent) {
            ff.setDescription(getMatchManager().getContextRenderer().getDescription());
            getFileChooser().addChoosableFileFilter(ff);
            final int returnVal = getFileChooser().showSaveDialog(mainPanel);
            getFileChooser().removeChoosableFileFilter(ff);

            if (returnVal == JFileChooser.APPROVE_OPTION) {
                sourceLocation = getFileChooser().getSelectedFile().getAbsolutePath();
            }

            if (null != sourceLocation) {
                saveSource(sourceLocation);
            }

            setChanged();
            notifyObservers();
        }

        public void update(Observable o, Object arg) {
            setEnabled(null != getMatchManager() && null != source && null != getMatchManager().getContextRenderer()
                    // exclusivity because uses the same progress bar
                    && PERMITS == semSource.availablePermits());
        }
    }

    private class ActionTargetCreate extends AbstractAction implements Observer {
        public ActionTargetCreate() {
            super("Create");
            putValue(Action.SHORT_DESCRIPTION, "Creates Target");
            putValue(Action.LONG_DESCRIPTION, "Creates Target Context");
            putValue(Action.SMALL_ICON, iconContextCreateSmall);
            putValue(Action.LARGE_ICON_KEY, iconContextCreateLarge);
        }

        public void actionPerformed(ActionEvent actionEvent) {
            if (acMappingClose.isEnabled()) {
                acMappingClose.actionPerformed(actionEvent);
            }
            if (!acMappingClose.isEnabled()) {
                if (acTargetClose.isEnabled()) {
                    acTargetClose.actionPerformed(actionEvent);
                }
                if (!acTargetClose.isEnabled()) {
                    target = getMatchManager().createContext();
                    target.createRoot("Top");
                    if (null != source) {
                        mapping = getMatchManager().getMappingFactory().getContextMappingInstance(source, target);
                        resetMappingInTable();
                        resetMappingInModel(tSource);
                    }
                    createTree(target, tTarget, mapping);
                    targetLocation = null;
                    targetModified = false;
                }
            }

            setChanged();
            notifyObservers();
        }

        public void update(Observable o, Object arg) {
            setEnabled(null != getMatchManager() && PERMITS == semTarget.availablePermits());
        }
    }

    private class ActionTargetOpen extends ActionTreeOpen implements Observer {
        public ActionTargetOpen() {
            super("Open...");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_O));
            putValue(Action.SHORT_DESCRIPTION, "Opens Target");
            putValue(Action.LONG_DESCRIPTION, "Opens Target Context");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
            putValue(Action.SMALL_ICON, documentOpenSmall);
            putValue(Action.LARGE_ICON_KEY, documentOpenLarge);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (acTargetClose.isEnabled()) {
                acTargetClose.actionPerformed(actionEvent);
            }
            if (!acTargetClose.isEnabled()) {
                super.actionPerformed(actionEvent);
                targetModified = false;
            }
            setChanged();
            notifyObservers();
        }

        @Override
        public void update(Observable o, Object arg) {
            super.update(o, arg);
            setEnabled(isEnabled() && PERMITS == semTarget.availablePermits());
        }

        @Override
        protected void open(File file) {
            openTarget(file);
        }
    }

    private class ActionTargetPreprocess extends AbstractAction implements Observer {
        public ActionTargetPreprocess() {
            super("Preprocess");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_P));
            putValue(Action.SHORT_DESCRIPTION, "Preprocesses Target");
            putValue(Action.LONG_DESCRIPTION, "Preprocesses Target Context");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.SHIFT_DOWN_MASK));
        }

        public void actionPerformed(ActionEvent actionEvent) {
            SwingWorker<Void, Void> offlineTask = createContextOfflineTask(target, semTarget, pbTargetProgress, false);
            offlineTask.execute();
        }

        public void update(Observable o, Object arg) {
            setEnabled(null != getMatchManager() && null != target && null != getMatchManager().getContextPreprocessor()
                    && PERMITS == semTarget.availablePermits());
        }
    }

    private class ActionTargetClose extends AbstractAction implements Observer {
        public ActionTargetClose() {
            super("Close");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_C));
            putValue(Action.SHORT_DESCRIPTION, "Closes Target");
            putValue(Action.LONG_DESCRIPTION, "Closes Target Context");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        }

        public void actionPerformed(ActionEvent actionEvent) {
            if (acMappingClose.isEnabled()) {
                acMappingClose.actionPerformed(actionEvent);
            }
            if (!acMappingClose.isEnabled()) {
                int choice = 1;//no, don't save.
                if (targetModified) {
                    choice = JOptionPane.showOptionDialog(frame,
                            "The target context has been changed.\n\nSave the target context?",
                            "Save the target context?",
                            JOptionPane.YES_NO_CANCEL_OPTION,
                            JOptionPane.QUESTION_MESSAGE,
                            null,
                            null,
                            0);
                }
                switch (choice) {
                    case 0: {//yes, save
                        acTargetSave.actionPerformed(actionEvent);
                        if (!acTargetSave.isEnabled()) {
                            closeTarget();
                        }
                        break;
                    }
                    case 1: {//no, don't save
                        closeTarget();
                        break;
                    }
                    case 2: {//cancel
                        break;
                    }
                    default: {//cancel
                    }
                }
            }
        }

        public void update(Observable o, Object arg) {
            setEnabled(null != getMatchManager() && null != target && PERMITS == semTarget.availablePermits());
        }
    }

    private class ActionTargetSave extends AbstractAction implements Observer {
        public ActionTargetSave() {
            super("Save");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_S));
            putValue(Action.SHORT_DESCRIPTION, "Saves Target");
            putValue(Action.LONG_DESCRIPTION, "Saves Target Context");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
            putValue(Action.SMALL_ICON, documentSaveSmall);
            putValue(Action.LARGE_ICON_KEY, documentSaveLarge);
        }

        public void actionPerformed(ActionEvent actionEvent) {
            if (null == targetLocation) {
                ff.setDescription(getMatchManager().getContextRenderer().getDescription());
                getFileChooser().addChoosableFileFilter(ff);
                final int returnVal = getFileChooser().showSaveDialog(mainPanel);
                getFileChooser().removeChoosableFileFilter(ff);

                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    targetLocation = getFileChooser().getSelectedFile().getAbsolutePath();
                }
            }

            if (null != targetLocation) {
                saveTarget(targetLocation);
            }

            setChanged();
            notifyObservers();
        }

        public void update(Observable o, Object arg) {
            setEnabled(null != getMatchManager() && null != target && null != getMatchManager().getContextRenderer()
                    && targetModified
                    // exclusivity because uses the same progress bar
                    && PERMITS == semTarget.availablePermits());
        }
    }

    private class ActionTargetSaveAs extends AbstractAction implements Observer {
        public ActionTargetSaveAs() {
            super("Save As...");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_A));
            putValue(Action.SHORT_DESCRIPTION, "Saves Target");
            putValue(Action.LONG_DESCRIPTION, "Saves Target Context");
            putValue(Action.SMALL_ICON, documentSaveAsSmall);
            putValue(Action.LARGE_ICON_KEY, documentSaveAsLarge);
        }

        public void actionPerformed(ActionEvent actionEvent) {
            ff.setDescription(getMatchManager().getContextRenderer().getDescription());
            getFileChooser().addChoosableFileFilter(ff);
            final int returnVal = getFileChooser().showSaveDialog(mainPanel);
            getFileChooser().removeChoosableFileFilter(ff);

            if (returnVal == JFileChooser.APPROVE_OPTION) {
                targetLocation = getFileChooser().getSelectedFile().getAbsolutePath();
            }

            if (null != targetLocation) {
                saveTarget(targetLocation);
            }

            setChanged();
            notifyObservers();
        }

        public void update(Observable o, Object arg) {
            setEnabled(null != getMatchManager() && null != target && null != getMatchManager().getContextRenderer()
                    // exclusivity because uses the same progress bar
                    && PERMITS == semTarget.availablePermits());
        }
    }

    private abstract class ActionTreeEdit extends AbstractAction implements Observer {
        protected JTree tree;

        public ActionTreeEdit(String name) {
            super(name);
            tree = null;
        }

        protected void doAction(JTree tree) {
            if (tSource == tree) {
                sourceModified = true;
            } else if (tTarget == tree) {
                targetModified = true;
            }
        }

        public void actionPerformed(ActionEvent actionEvent) {
            if (null == tree) {
                doAction(lastFocusedTree);
            } else {
                doAction(tree);
            }

            setChanged();
            notifyObservers();
        }

        protected abstract void setEnabled(JTree tree);

        public void update(Observable o, Object arg) {
            if (null == tree) {
                setEnabled(lastFocusedTree);
            } else {
                setEnabled(tree);
            }
        }
    }

    private class ActionEditAddNode extends ActionTreeEdit implements Observer {

        public ActionEditAddNode() {
            super("Add Node");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_N));
            putValue(Action.SHORT_DESCRIPTION, "Adds a node");
            putValue(Action.LONG_DESCRIPTION, "Adds a node");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0));
            putValue(Action.SMALL_ICON, iconAddNodeSmall);
            putValue(Action.LARGE_ICON_KEY, iconAddNodeLarge);
        }

        public ActionEditAddNode(JTree tree) {
            this();
            this.tree = tree;
            putValue(Action.ACCELERATOR_KEY, null);
        }

        @Override
        protected void doAction(JTree tree) {
            super.doAction(tree);
            addNode(tree);
        }

        @Override
        public void setEnabled(JTree tree) {
            setEnabled(null != tree && 1 == tree.getSelectionCount()
                            && (tree.getSelectionPath().getLastPathComponent() instanceof INode)
                            && ((INode) tree.getSelectionPath().getLastPathComponent()).hasParent()
                            && (tree == tSource ? PERMITS == semSource.availablePermits() : PERMITS == semTarget.availablePermits())
            );
        }
    }

    private class ActionEditAddChildNode extends ActionTreeEdit implements Observer {

        public ActionEditAddChildNode() {
            super("Add Child Node");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_C));
            putValue(Action.SHORT_DESCRIPTION, "Adds a child node");
            putValue(Action.LONG_DESCRIPTION, "Adds a child node to a node");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, InputEvent.ALT_DOWN_MASK));
            putValue(Action.SMALL_ICON, iconAddChildNodeSmall);
            putValue(Action.LARGE_ICON_KEY, iconAddChildNodeLarge);
        }

        public ActionEditAddChildNode(JTree tree) {
            this();
            this.tree = tree;
            putValue(Action.ACCELERATOR_KEY, null);
        }

        @Override
        protected void doAction(JTree tree) {
            super.doAction(tree);
            addChildNode(tree);
        }

        @Override
        public void setEnabled(JTree tree) {
            setEnabled(null != tree && 1 == tree.getSelectionCount()
                            && (tree.getSelectionPath().getLastPathComponent() instanceof INode
                            && (tree == tSource ? PERMITS == semSource.availablePermits() : PERMITS == semTarget.availablePermits())
                    )
            );
        }
    }

    private class ActionEditAddLink extends AbstractAction implements Observer {
        public ActionEditAddLink() {
            super("Add Link");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_L));
            putValue(Action.SHORT_DESCRIPTION, "Adds a link");
            putValue(Action.LONG_DESCRIPTION, "Adds a link");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
            putValue(Action.SMALL_ICON, iconAddLinkSmall);
            putValue(Action.LARGE_ICON_KEY, iconAddLinkLarge);
        }

        public void actionPerformed(ActionEvent actionEvent) {
            if ((tSource.getSelectionPath().getLastPathComponent() instanceof INode)
                    && (tTarget.getSelectionPath().getLastPathComponent() instanceof INode)) {
                INode sourceNode = (INode) tSource.getSelectionPath().getLastPathComponent();
                INode targetNode = (INode) tTarget.getSelectionPath().getLastPathComponent();

                if (null == mapping) {
                    JTree oldLastFocusedTree = lastFocusedTree;
                    mapping = getMatchManager().getMappingFactory().getContextMappingInstance(source, target);
                    resetMappingInModel(tSource);
                    resetMappingInModel(tTarget);
                    resetMappingInTable();
                    lastFocusedTree = oldLastFocusedTree;
                }

                TreePath sourcePath = createPathToRoot(sourceNode);
                TreePath targetPath = createPathToRoot(targetNode);

                if (sourceNode.nodeData().getUserObject() instanceof List
                        && targetNode.nodeData().getUserObject() instanceof List) {

                    @SuppressWarnings("unchecked")
                    List<DefaultMutableTreeNode> sourceLinkNodes = (List<DefaultMutableTreeNode>) sourceNode.nodeData().getUserObject();
                    @SuppressWarnings("unchecked")
                    List<DefaultMutableTreeNode> targetLinkNodes = (List<DefaultMutableTreeNode>) targetNode.nodeData().getUserObject();
                    DefaultMutableTreeNode sourceLinkNode;
                    DefaultMutableTreeNode targetLinkNode;

                    // check mapping
                    char existingRel = mapping.getRelation(sourceNode, targetNode);
                    boolean edit = false;
                    if (IMappingElement.IDK == existingRel) {
                        // add the mapping
                        mapping.setRelation(sourceNode, targetNode, IMappingElement.EQUIVALENCE);
                        IMappingElement<INode> me = new MappingElement<>(sourceNode, targetNode, IMappingElement.EQUIVALENCE);
                        // add the link nodes
                        sourceLinkNode = new DefaultMutableTreeNode(me);
                        if (0 == sourceLinkNodes.size()) {
                            sourceLinkNodes = new ArrayList<>();
                            sourceNode.nodeData().setUserObject(sourceLinkNodes);
                        }
                        sourceLinkNodes.add(sourceLinkNode);
                        targetLinkNode = new DefaultMutableTreeNode(me);
                        if (0 == targetLinkNodes.size()) {
                            targetLinkNodes = new ArrayList<>();
                            targetNode.nodeData().setUserObject(targetLinkNodes);
                        }
                        targetLinkNodes.add(targetLinkNode);

                        // signal insertion
                        if (tSource.getModel() instanceof DefaultTreeModel
                                && tTarget.getModel() instanceof DefaultTreeModel) {
                            DefaultTreeModel dtmSource = (DefaultTreeModel) tSource.getModel();
                            DefaultTreeModel dtmTarget = (DefaultTreeModel) tTarget.getModel();
                            int sIdx = dtmSource.getIndexOfChild(sourceNode, sourceLinkNode);
                            int tIdx = dtmTarget.getIndexOfChild(targetNode, targetLinkNode);
                            dtmSource.nodesWereInserted(sourceNode, new int[]{sIdx});
                            dtmTarget.nodesWereInserted(targetNode, new int[]{tIdx});
                        }

                        //signal mapping table
                        if (tblMapping.getModel() instanceof MappingTableModel) {
                            MappingTableModel mtm = (MappingTableModel) tblMapping.getModel();
                            mtm.fireElementInserted(me);
                        }

                    } else {
                        // find them
                        sourceLinkNode = findLinkNode(sourceNode, targetNode, sourceLinkNodes, existingRel);
                        targetLinkNode = findLinkNode(sourceNode, targetNode, targetLinkNodes, existingRel);
                        edit = true;
                    }

                    sourcePath = sourcePath.pathByAddingChild(sourceLinkNode);
                    targetPath = targetPath.pathByAddingChild(targetLinkNode);
                    // start editing
                    if (null != lastFocusedTree) {
                        TreePath tp;
                        if (lastFocusedTree == tSource) {
                            tp = sourcePath;
                        } else {
                            tp = targetPath;
                        }
                        lastFocusedTree.setSelectionPath(tp);
                        lastFocusedTree.scrollPathToVisible(tp);
                        if (edit) {
                            lastFocusedTree.startEditingAtPath(tp);
                        }
                    }

                    mappingModified = true;
                    setChanged();
                    notifyObservers();
                }
            }
        }

        private DefaultMutableTreeNode findLinkNode(INode sourceNode, INode targetNode, List<DefaultMutableTreeNode> linkNodes, char existingRel) {
            DefaultMutableTreeNode result = null;
            for (DefaultMutableTreeNode linkNode : linkNodes) {
                if (linkNode.getUserObject() instanceof IMappingElement) {
                    @SuppressWarnings("unchecked")
                    IMappingElement<INode> me = (IMappingElement<INode>) linkNode.getUserObject();
                    if (me.getSource() == sourceNode && me.getTarget() == targetNode && getRelation(me) == existingRel) {
                        result = linkNode;
                        break;
                    }
                }
            }
            return result;
        }

        public void update(Observable o, Object arg) {
            setEnabled(null != getMatchManager() &&
                            null != source && 1 == tSource.getSelectionCount() && (tSource.getSelectionPath().getLastPathComponent() instanceof INode)
                            &&
                            null != target && 1 == tTarget.getSelectionCount() && (tTarget.getSelectionPath().getLastPathComponent() instanceof INode)
                            && PERMITS == semMapping.availablePermits()
            );
        }
    }

    private class ActionEditDelete extends ActionTreeEdit implements Observer {

        public ActionEditDelete() {
            super("Delete");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_D));
            putValue(Action.SHORT_DESCRIPTION, "Deletes a node or a link");
            putValue(Action.LONG_DESCRIPTION, "Deletes a node or a link");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
            putValue(Action.SMALL_ICON, iconDeleteSmall);
            putValue(Action.LARGE_ICON_KEY, iconDeleteLarge);
        }

        public ActionEditDelete(JTree tree) {
            this();
            this.tree = tree;
            putValue(Action.ACCELERATOR_KEY, null);
        }

        @Override
        protected void doAction(JTree tree) {
            deleteNode(tree);
        }

        @Override
        public void setEnabled(JTree tree) {
            boolean result = null != tree
                    && 1 == tree.getSelectionCount()
                    && null != tree.getSelectionPath()
                    && null != tree.getSelectionPath().getParentPath()
                    && null != tree.getSelectionPath().getParentPath().getLastPathComponent()
                    && (tree == tSource ? PERMITS == semSource.availablePermits() : PERMITS == semTarget.availablePermits());

            // disable deletion for subs (...) nodes
            if (result) {
                if (null != tree.getSelectionPath() &&
                        tree.getSelectionPath().getLastPathComponent() instanceof DefaultMutableTreeNode) {
                    DefaultMutableTreeNode dmtn = (DefaultMutableTreeNode) tree.getSelectionPath().getLastPathComponent();
                    result = dmtn.getUserObject() instanceof IMappingElement;
                }
            }

            setEnabled(result);
        }
    }

    private class ActionViewUncoalesce extends ActionTreeEdit implements Observer {
        public ActionViewUncoalesce() {
            super("Uncoalesce");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_U));
            putValue(Action.SHORT_DESCRIPTION, "Uncoalesces a node");
            putValue(Action.LONG_DESCRIPTION, "Uncoalesces a node");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, 0));
        }

        public ActionViewUncoalesce(JTree tree) {
            this();
            this.tree = tree;
            putValue(Action.ACCELERATOR_KEY, null);
        }

        @Override
        protected void doAction(JTree tree) {
            uncoalesceNode(tree);
        }

        @Override
        public void setEnabled(JTree tree) {
            boolean result = null != tree
                    && 1 == tree.getSelectionCount()
                    && null != tree.getSelectionPath().getParentPath()
                    && null != tree.getSelectionPath().getParentPath().getLastPathComponent();

            if (result) {
                result = tree.getSelectionPath().getLastPathComponent() instanceof DefaultMutableTreeNode;
                if (result) {
                    DefaultMutableTreeNode dmtn = (DefaultMutableTreeNode) tree.getSelectionPath().getLastPathComponent();
                    result = dmtn.getUserObject() instanceof MappingTreeModel.Coalesce;
                }
            }

            setEnabled(result);
        }
    }

    private class ActionViewUncoalesceAll extends ActionTreeEdit implements Observer {
        public ActionViewUncoalesceAll() {
            super("Uncoalesce All");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_A));
            putValue(Action.SHORT_DESCRIPTION, "Uncoalesces all nodes");
            putValue(Action.LONG_DESCRIPTION, "Uncoalesces all nodes");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, InputEvent.ALT_DOWN_MASK));
            putValue(Action.SMALL_ICON, iconUncoalesceSmall);
            putValue(Action.LARGE_ICON_KEY, iconUncoalesceLarge);
        }

        public ActionViewUncoalesceAll(JTree tree) {
            this();
            this.tree = tree;
            putValue(Action.ACCELERATOR_KEY, null);
        }

        @Override
        protected void doAction(JTree tree) {
            uncoalesceTree(tree);
        }

        @Override
        public void setEnabled(JTree tree) {
            boolean result = null != tree
                    && tree.getModel() instanceof MappingTreeModel;

            if (result) {
                MappingTreeModel mtm = (MappingTreeModel) tree.getModel();
                result = mtm.hasCoalescedNode();
            }

            setEnabled(result);
        }
    }

    private class ActionMappingCreate extends AbstractAction implements Observer {
        public ActionMappingCreate() {
            super("Create");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_R));
            putValue(Action.SHORT_DESCRIPTION, "Creates Mapping");
            putValue(Action.LONG_DESCRIPTION, "Creates Mapping between Contexts");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
        }

        public void actionPerformed(ActionEvent actionEvent) {
            if (acMappingClose.isEnabled()) {
                acMappingClose.actionPerformed(actionEvent);
            }
            if (!acMappingClose.isEnabled()) {
                SwingWorker<IContextMapping<INode>, IMappingElement<INode>> task = createMatchTask();
                task.execute();
            }
        }

        public void update(Observable o, Object arg) {
            setEnabled(null != getMatchManager() && null != source && null != target
                    && PERMITS == semMapping.availablePermits());
        }
    }

    private class ActionMappingOpen extends AbstractAction implements Observer {
        public ActionMappingOpen() {
            super("Open...");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_O));
            putValue(Action.SHORT_DESCRIPTION, "Opens Mapping");
            putValue(Action.LONG_DESCRIPTION, "Opens Mapping Context");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
            putValue(Action.SMALL_ICON, documentOpenSmall);
            putValue(Action.LARGE_ICON_KEY, documentOpenLarge);
        }

        public void actionPerformed(ActionEvent actionEvent) {
            if (acMappingClose.isEnabled()) {
                acMappingClose.actionPerformed(actionEvent);
            }
            if (!acMappingClose.isEnabled()) {
                ff.setDescription(getMatchManager().getMappingLoader().getDescription());
                getFileChooser().addChoosableFileFilter(ff);
                final int returnVal = getFileChooser().showOpenDialog(mainPanel);
                getFileChooser().removeChoosableFileFilter(ff);

                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    openMapping(getFileChooser().getSelectedFile());
                    mappingModified = false;
                }
            }
            setChanged();
            notifyObservers();
        }

        public void update(Observable o, Object arg) {
            setEnabled(null != getMatchManager() && null != source && null != target && null != getMatchManager().getMappingLoader()
                    && PERMITS == semMapping.availablePermits());
        }
    }

    private class ActionMappingClose extends AbstractAction implements Observer {
        public ActionMappingClose() {
            super("Close");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_C));
            putValue(Action.SHORT_DESCRIPTION, "Closes Mapping");
            putValue(Action.LONG_DESCRIPTION, "Closes Mapping Context");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
        }

        public void actionPerformed(ActionEvent actionEvent) {
            int choice = 1;//no, don't save.
            if (mappingModified) {
                choice = JOptionPane.showOptionDialog(frame,
                        "The mapping has been changed.\n\nSave the mapping?",
                        "Save the mapping?",
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        null,
                        0);
            }
            switch (choice) {
                case 0: {//yes, save
                    acMappingSave.actionPerformed(actionEvent);
                    if (!acMappingSave.isEnabled()) {
                        closeMapping();
                    }
                    break;
                }
                case 1: {//no, don't save
                    closeMapping();
                    break;
                }
                case 2: {//cancel
                    break;
                }
                default: {//cancel
                }
            }
        }

        public void update(Observable o, Object arg) {
            setEnabled(null != getMatchManager() && null != mapping && PERMITS == semMapping.availablePermits());
        }
    }

    private class ActionMappingSave extends AbstractAction implements Observer {
        public ActionMappingSave() {
            super("Save");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_S));
            putValue(Action.SHORT_DESCRIPTION, "Saves Mapping");
            putValue(Action.LONG_DESCRIPTION, "Saves Mapping Context");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
            putValue(Action.SMALL_ICON, documentSaveSmall);
            putValue(Action.LARGE_ICON_KEY, documentSaveLarge);
        }

        public void actionPerformed(ActionEvent actionEvent) {
            if (null == mappingLocation) {
                askMappingLocation();
            }

            saveMapping();
        }

        public void update(Observable o, Object arg) {
            setEnabled(null != getMatchManager() && null != mapping && null != getMatchManager().getMappingRenderer()
                    && mappingModified
                    // exclusivity because uses the same progress bar
                    && PERMITS == semMapping.availablePermits());
        }
    }

    private class ActionMappingSaveAs extends AbstractAction implements Observer {
        public ActionMappingSaveAs() {
            super("Save As...");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_A));
            putValue(Action.SHORT_DESCRIPTION, "Saves Mapping");
            putValue(Action.LONG_DESCRIPTION, "Saves Mapping Context");
            putValue(Action.SMALL_ICON, documentSaveAsSmall);
            putValue(Action.LARGE_ICON_KEY, documentSaveAsLarge);
        }

        public void actionPerformed(ActionEvent actionEvent) {
            askMappingLocation();
            saveMapping();
        }

        public void update(Observable o, Object arg) {
            setEnabled(null != getMatchManager() && null != mapping && null != getMatchManager().getMappingRenderer()
                    // exclusivity because uses the same progress bar
                    && PERMITS == semMapping.availablePermits());
        }
    }

    private class ActionViewClearLog extends AbstractAction {
        public ActionViewClearLog() {
            super("Clear Log");
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_L));
            putValue(Action.SHORT_DESCRIPTION, "Clears log");
            putValue(Action.LONG_DESCRIPTION, "Clears log window");
        }

        public void actionPerformed(ActionEvent actionEvent) {
            taLog.setText("");
        }
    }

    private class PopupListener extends MouseAdapter {
        public void mousePressed(MouseEvent e) {
            maybeShowPopup(e);
        }

        public void mouseReleased(MouseEvent e) {
            maybeShowPopup(e);
        }

        private void maybeShowPopup(MouseEvent e) {
            if (e.isPopupTrigger()) {
                if (e.getComponent() == tSource) {
                    popSource.show(e.getComponent(), e.getX(), e.getY());
                } else if (e.getComponent() == tTarget) {
                    popTarget.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        }
    }

    private final PopupListener treePopupListener = new PopupListener();

    //listener for config files combobox
    private final ItemListener configComboListener = new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
            if ((e.getSource() == cbConfig) && (e.getStateChange() == ItemEvent.SELECTED)) {
                if (null != getMatchManager()) {
                    configFileName = (new File(GUI_CONF_FILE)).getParent() + File.separator + e.getItem();
                    updateMatchManagerConfig(configFileName);
                }
            }
        }
    };

    private final MouseListener treeMouseListener = new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
            if (null != lastFocusedTree) {
                if (e.getClickCount() == 2) {
                    if (acViewUncoalesce.isEnabled()) {
                        uncoalesceNode(lastFocusedTree);
                    }
                }
            }
        }
    };

    private final FocusListener treeFocusListener = new FocusListener() {
        public void focusGained(FocusEvent e) {
            if (!e.isTemporary()) {
                final Component c = e.getComponent();
                if (c instanceof JTree) {
                    final JTree t = (JTree) c;
                    if (t == tSource || t == tTarget) {
                        lastFocusedTree = t;
                        t.addTreeSelectionListener(treeSelectionListener);
                        // fire the event for the first time
                        TreeSelectionEvent tse = new TreeSelectionEvent(t, t.getSelectionPath(), true, null, t.getSelectionPath());
                        treeSelectionListener.valueChanged(tse);
                    }
                }
            }
        }

        public void focusLost(FocusEvent e) {
            if (!e.isTemporary()) {
                final Component c = e.getComponent();
                if (c instanceof JTree) {
                    final JTree t = (JTree) c;
                    if (t == tSource || t == tTarget) {
                        t.removeTreeSelectionListener(treeSelectionListener);
                    }
                }
            }
        }
    };

    private final ListSelectionListener tableSelectionListener = new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
            MappingTableModel mtm = (MappingTableModel) tblMapping.getModel();
            if (-1 != tblMapping.getSelectedRow()) {
                IMappingElement<INode> me = mtm.getElementAt(tblMapping.getSelectedRow());
                if (null != me) {
                    //select source node in source tree
                    uncoalesceTree(tSource);
                    //find the link node
                    DefaultMutableTreeNode linkNode = null;
                    @SuppressWarnings("unchecked")
                    List<DefaultMutableTreeNode> linkNodes = (List<DefaultMutableTreeNode>) me.getSource().nodeData().getUserObject();
                    MappingTreeModel matm = (MappingTreeModel) tSource.getModel();
                    if (null == linkNodes) {
                        linkNodes = matm.updateUserObject(me.getSource());
                    }
                    for (DefaultMutableTreeNode dmtn : linkNodes) {
                        if (me.equals(dmtn.getUserObject())) {
                            linkNode = dmtn;
                            break;
                        }
                    }
                    if (null != linkNode) {
                        // construct path to root
                        TreePath pp = createPathToRoot(me.getSource());
                        pp = pp.pathByAddingChild(linkNode);
                        List<Object> ppList = Arrays.asList(pp.getPath());
                        tSource.makeVisible(pp);
                        tSource.setSelectionPath(pp);
                        tSource.scrollPathToVisible(pp);

                        // check whether root is visible
                        if (!isRootVisible(tSource)) {
                            // first try collapsing all the nodes above the target one
                            INode curNode = me.getSource();
                            while (null != curNode) {
                                for (INode child : curNode.getChildren()) {
                                    if (!ppList.contains(child) && !matm.isCoalesced(child)) {
                                        tSource.collapsePath(createPathToRoot(child));
                                    }
                                }
                                curNode = curNode.getParent();
                            }

                            tSource.scrollPathToVisible(pp);
                            if (!isRootVisible(tSource)) {
                                // second try coalescing nodes, starting from those in the top
                                for (int i = 0; i < (ppList.size() - 1); i++) {
                                    if (ppList.get(i) instanceof INode) {
                                        INode node = (INode) ppList.get(i);
                                        int idx = tSource.getModel().getIndexOfChild(node, ppList.get(i + 1));
                                        matm.coalesce(node, 0, idx - 1);

                                        tSource.scrollPathToVisible(pp);
                                        if (isRootVisible(tSource)) {
                                            break;
                                        }
                                    }
                                }
                            }
                            scrollToTop(spSource);
                        }

                        treeSelectionListener.adjustTargetTree(tSource, tTarget, spSource, spTarget, me.getSource(), me.getTarget());
                        if (null == lastFocusedTree) {
                            //"focus" source tree because we select link node there
                            //and then delete works
                            lastFocusedTree = tSource;
                        }
                        //to update actions of selection change
                        setChanged();
                        notifyObservers();
                    } else {
                        //shouldn't happen
                        log.error("Can't find link node!");
                    }
                }
            }
        }
    };

    private void scrollToTop(JScrollPane scrollPane) {
        scrollPane.getViewport().setViewPosition(new Point(scrollPane.getViewport().getViewPosition().x, 0));
    }

    private final AdjustingTreeSelectionListener treeSelectionListener = new AdjustingTreeSelectionListener();

    private class AdjustingTreeSelectionListener implements TreeSelectionListener {
        public void valueChanged(TreeSelectionEvent e) {
            if (e.getSource() instanceof JTree) {
                TreePath p = e.getNewLeadSelectionPath();
                if (null != p) {
                    Object o = p.getLastPathComponent();
                    if (o instanceof DefaultMutableTreeNode) {
                        DefaultMutableTreeNode dmtn = (DefaultMutableTreeNode) o;
                        if (dmtn.getUserObject() instanceof IMappingElement) {
                            @SuppressWarnings("unchecked")
                            IMappingElement<INode> me = (IMappingElement<INode>) dmtn.getUserObject();
                            if (e.getSource() == tSource) {
                                adjustTargetTree(tSource, tTarget, spSource, spTarget, me.getSource(), me.getTarget());
                            } else if (e.getSource() == tTarget) {
                                adjustTargetTree(tTarget, tSource, spTarget, spSource, me.getTarget(), me.getSource());
                            }
                            adjustMappingTable(me);
                        }
                    }
                }
            }

            setChanged();
            notifyObservers();
        }

        public void adjustTargetTree(JTree tSource, JTree tTarget, JScrollPane spSource, JScrollPane spTarget, INode source, INode target) {
            if (tTarget.getModel() instanceof MappingTreeModel) {
                MappingTreeModel mtm = (MappingTreeModel) tTarget.getModel();
                mtm.uncoalesceParents(target);

                // construct path to root
                TreePath pp = createPathToRoot(target);
                List<Object> ppList = Arrays.asList(pp.getPath());

                tTarget.makeVisible(pp);
                tTarget.setSelectionPath(pp);
                tTarget.scrollPathToVisible(pp);

                // scroll to match vertical position
                if (1 == tSource.getSelectionCount() && 1 == tTarget.getSelectionCount()) {
                    scrollToMatchVerticalPosition(tSource, tTarget, spSource, spTarget);

                    // check whether root is visible
                    if (!isRootVisible(tTarget)) {
                        // first try collapsing all the nodes above the target one
                        INode curNode = target.getParent();
                        while (null != curNode) {
                            for (INode child : curNode.getChildren()) {
                                if (!ppList.contains(child) && !mtm.isCoalesced(child)) {
                                    tTarget.collapsePath(createPathToRoot(child));
                                }
                            }
                            curNode = curNode.getParent();
                        }

                        scrollToMatchVerticalPosition(tSource, tTarget, spSource, spTarget);
                        if (!isRootVisible(tTarget)) {
                            // second try coalescing nodes, starting from those in the top
                            for (int i = 0; i < ppList.size() - 1; i++) {
                                if (ppList.get(i) instanceof INode && ppList.get(i + 1) instanceof INode) {
                                    INode node = (INode) ppList.get(i);
                                    INode child = (INode) ppList.get(i + 1);
                                    int idx = node.getChildIndex(child);
                                    mtm.coalesce(node, 0, idx - 1);

                                    scrollToMatchVerticalPosition(tSource, tTarget, spSource, spTarget);
                                    if (isRootVisible(tTarget)) {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                spTarget.repaint();
            }
        }
    }

    private void adjustMappingTable(IMappingElement<INode> me) {
        if (tblMapping.getModel() instanceof MappingTableModel) {
            //to avoid cycle -> tree scrolled -> we scroll table -> it scrolls tree
            tblMapping.getSelectionModel().removeListSelectionListener(tableSelectionListener);
            MappingTableModel mtm = (MappingTableModel) tblMapping.getModel();
            int idx = mtm.getIndexOf(me);
            if (-1 != idx) {
                tblMapping.getSelectionModel().setSelectionInterval(idx, idx);
                tblMapping.scrollRectToVisible(tblMapping.getCellRect(idx, 0, true));
            }
            tblMapping.getSelectionModel().addListSelectionListener(tableSelectionListener);
        }
    }

    public static boolean isRootVisible(JTree tTarget) {
        boolean result = false;
        if (tTarget.getModel() instanceof BaseCoalesceTreeModel) {
            BaseCoalesceTreeModel mtm = (BaseCoalesceTreeModel) tTarget.getModel();
            if (mtm.getRoot() instanceof IBaseNode) {
                IBaseNode root = (IBaseNode) mtm.getRoot();
                TreePath rootPath = new TreePath(root);
                result = tTarget.getVisibleRect().contains(tTarget.getPathBounds(rootPath));
            }
        }
        return result;
    }

    /**
     * Scroll <code>spTarget</code> to match vertical position of <code>tTarget</code>'s selected node to the
     * vertical position of <code>tSource</code>'s selected node.
     *
     * @param tSource  source tree
     * @param tTarget  target tree
     * @param spSource JScroll pane which contains source tree
     * @param spTarget JScroll pane which contains target tree
     */
    private void scrollToMatchVerticalPosition(JTree tSource, JTree tTarget, JScrollPane spSource, JScrollPane spTarget) {
        if (0 < tSource.getSelectionCount() && 0 < tTarget.getSelectionCount()) {
            if (null != tSource.getSelectionRows() && null != tTarget.getSelectionRows()) {
                int sourceSelRowIdx = tSource.getSelectionRows()[0];
                int targetSelRowIdx = tTarget.getSelectionRows()[0];
                Rectangle sr = tSource.getRowBounds(sourceSelRowIdx);
                Rectangle tr = tTarget.getRowBounds(targetSelRowIdx);
                Point sp = spSource.getViewport().getViewPosition();
                Point tp = spTarget.getViewport().getViewPosition();
                int delta = (tr.y - tp.y) - (sr.y - sp.y);
                spTarget.getViewport().setViewPosition(new Point(tp.x, tp.y + delta));
            }
        }
    }

    private class MappingTreeCellRenderer extends DefaultTreeCellRenderer {
        public MappingTreeCellRenderer() {
            super();
            setLeafIcon(folderSmall);
            setClosedIcon(folderSmall);
            setOpenIcon(folderOpenSmall);
        }

        public Component getTreeCellRendererComponent(final JTree tree, final Object value,
                                                      final boolean sel,
                                                      final boolean expanded,
                                                      final boolean leaf, final int row,
                                                      final boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            setToolTipText("");
            if (value instanceof INode) {
                INode node = (INode) value;
                if (0 == node.getChildCount()) {
                    setIcon(folderSmall);
                } else if (expanded) {
                    setIcon(folderOpenSmall);
                } else {
                    setIcon(folderSmall);
                }
            } else {
                if (value instanceof DefaultMutableTreeNode) {
                    DefaultMutableTreeNode dmtn = (DefaultMutableTreeNode) value;
                    if (dmtn.getUserObject() instanceof IMappingElement) {
                        @SuppressWarnings("unchecked")
                        IMappingElement<INode> me = (IMappingElement<INode>) dmtn.getUserObject();
                        char relation = IMappingElement.IDK;
                        if (null != mapping) {
                            relation = getRelation(me);
                        }
                        if (tree == tSource) {
                            setText(me.getTarget().nodeData().getName());
                            //TODO links with the same named target in different contexts - add tooltips
                            switch (relation) {
                                case IMappingElement.LESS_GENERAL: {
                                    setIcon(iconLG);
                                    break;
                                }
                                case IMappingElement.MORE_GENERAL: {
                                    setIcon(iconMG);
                                    break;
                                }
                            }
                        } else {
                            setText(me.getSource().nodeData().getName());
                            //TODO links with the same named target in different contexts - add tooltips
                            switch (relation) {
                                case IMappingElement.LESS_GENERAL: {
                                    setIcon(iconMG);
                                    break;
                                }
                                case IMappingElement.MORE_GENERAL: {
                                    setIcon(iconLG);
                                    break;
                                }
                            }
                        }
                        switch (relation) {
                            case IMappingElement.EQUIVALENCE: {
                                setIcon(iconEQ);
                                break;
                            }
                            case IMappingElement.DISJOINT: {
                                setIcon(iconDJ);
                                break;
                            }
                        }
                    } else if (dmtn.getUserObject() instanceof MappingTreeModel.Coalesce) {
                        MappingTreeModel.Coalesce c = (MappingTreeModel.Coalesce) dmtn.getUserObject();
                        setText(ELLIPSIS);
                        setIcon(iconUncoalesceSmall);
                        StringBuilder tip = new StringBuilder();
                        @SuppressWarnings("unchecked")
                        List<DefaultMutableTreeNode> linkNodes = (List<DefaultMutableTreeNode>) c.parent.nodeData().getUserObject();
                        if (null == linkNodes) {
                            if (tree.getModel() instanceof MappingTreeModel) {
                                linkNodes = ((MappingTreeModel) tree.getModel()).updateUserObject(c.parent);
                            }
                        }
                        for (int i = c.range.x; i <= c.range.y; i++) {
                            if (i < c.parent.getChildCount()) {
                                tip.append(c.parent.getChildAt(i).nodeData().getName());
                                if (i < c.range.y) {
                                    tip.append(", ");
                                }
                            } else {
                                if (null != linkNodes) {
                                    if (linkNodes.get(i - c.parent.getChildCount()).getUserObject() instanceof IMappingElement) {
                                        @SuppressWarnings("unchecked")
                                        IMappingElement<INode> me = (IMappingElement<INode>) linkNodes.get(i - c.parent.getChildCount()).getUserObject();
                                        tip.append("->");
                                        if (tree == tSource) {
                                            tip.append(me.getTarget().nodeData().getName());
                                        } else {
                                            tip.append(me.getSource().nodeData().getName());
                                        }

                                        if (i < c.range.y) {
                                            tip.append(", ");
                                        }

                                    }
                                }
                            }
                        }
                        if (100 < tip.length()) {
                            tip.delete(50, tip.length() - 50);
                            tip.insert(50, "...");
                        }
                        setToolTipText(tip.toString());
                    }
                }
            }

            return this;
        }

    }

    private final MappingTreeCellRenderer mappingTreeCellRenderer = new MappingTreeCellRenderer();

    private class CustomFileFilter extends javax.swing.filechooser.FileFilter {

        private String description;

        public String getDescription() {
            return description;
        }

        public boolean accept(File file) {
            String ext = getExtension(file);
            return null != description && null != ext && description.contains(ext);
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getExtension(File f) {
            String ext = null;
            String s = f.getName();
            int i = s.lastIndexOf('.');

            if (i > 0 && i < s.length() - 1) {
                ext = s.substring(i + 1).toLowerCase();
            }
            return ext;
        }
    }

    private final CustomFileFilter ff = new CustomFileFilter();

    private class MappingTableCellRenderer extends DefaultTableCellRenderer {
        public MappingTableCellRenderer() {
            super();
        }

        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setToolTipText("");
            setIcon(null);
            if (value instanceof IMappingElement) {
                @SuppressWarnings("unchecked")
                IMappingElement<INode> me = (IMappingElement<INode>) value;
                switch (column) {
                    case MappingTableModel.C_SOURCE: {
                        setText(me.getSource().nodeData().getName());
                        setToolTipText(getStringPathToRoot(me.getSource()));
                        break;
                    }
                    case MappingTableModel.C_TARGET: {
                        setText(me.getTarget().nodeData().getName());
                        setToolTipText(getStringPathToRoot(me.getTarget()));
                        break;
                    }
                    case MappingTableModel.C_RELATION: {
                        setText(relationToDescription.get(getRelation(me)));
                        switch (getRelation(me)) {
                            case IMappingElement.LESS_GENERAL: {
                                setIcon(iconLG);
                                break;
                            }
                            case IMappingElement.MORE_GENERAL: {
                                setIcon(iconMG);
                                break;
                            }
                            case IMappingElement.EQUIVALENCE: {
                                setIcon(iconEQ);
                                break;
                            }
                            case IMappingElement.DISJOINT: {
                                setIcon(iconDJ);
                                break;
                            }
                        }
                        break;
                    }
                    default: {
                        break;
                    }
                }
            }

            return this;
        }

    }

    private String getStringPathToRoot(INode source) {
        StringBuilder result = new StringBuilder("");
        TreePath path = createPathToRoot(source);
        for (int i = 0; i < path.getPathCount(); i++) {
            INode node = (INode) path.getPathComponent(i);
            if (0 == i) {
                result.append(node.nodeData().getName());
            } else {
                result.append("\\").append(node.nodeData().getName());
            }
        }
        return result.toString();
    }

    private final MappingTableCellRenderer mappingTableCellRenderer = new MappingTableCellRenderer();


    private class NodeTreeCellEditor extends DefaultTreeCellEditor {

        private final TreeCellEditor oldRealEditor;
        private final TreeCellEditor comboEditor;
        private DefaultComboBox combo;

        private class DefaultComboBox extends JComboBox<String> implements FocusListener {//lifted from DefaultTreeCellEditor

            class ComboBoxRenderer extends JLabel implements ListCellRenderer<String> {

                public ComboBoxRenderer() {
                    setOpaque(true);
                    setVerticalAlignment(CENTER);
                }

                public Component getListCellRendererComponent(
                        JList list,
                        String value,
                        int index,
                        boolean isSelected,
                        boolean cellHasFocus) {

                    if (isSelected) {
                        setBackground(list.getSelectionBackground());
                        setForeground(list.getSelectionForeground());
                    } else {
                        setBackground(list.getBackground());
                        setForeground(list.getForeground());
                    }

                    setIcon(descriptionToIcon.get(value));
                    setText(value);
                    setFont(list.getFont());

                    return this;
                }
            }

            protected Border border;

            public DefaultComboBox(Border border) {
                setBorder(border);
                addFocusListener(this);
                setRenderer(new ComboBoxRenderer());
            }

            public void setBorder(Border border) {
                super.setBorder(border);
                this.border = border;
            }

            public Border getBorder() {
                return border;
            }

            public Font getFont() {
                Font font = super.getFont();

                if (font instanceof FontUIResource) {
                    Container parent = getParent();

                    if (parent != null && parent.getFont() != null)
                        font = parent.getFont();
                }
                return font;
            }

            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();

                // If not font has been set, prefer the renderers height.
                if (NodeTreeCellEditor.this.renderer != null && NodeTreeCellEditor.this.getFont() == null) {
                    Dimension rSize = NodeTreeCellEditor.this.renderer.getPreferredSize();
                    size.height = rSize.height;
                }
                return size;
            }

            public void focusGained(FocusEvent e) {
                if (!e.isTemporary()) {
                    if (null != mapping) {
                        this.showPopup();
                    }
                }
            }

            public void focusLost(FocusEvent e) {
                //nop
            }
        }

        private class LinkCellEditor extends DefaultCellEditor {
            public LinkCellEditor(final JComboBox comboBox) {
                super(comboBox);
                comboBox.removeActionListener(delegate);
                delegate = new EditorDelegate() {
                    @Override
                    public void setValue(Object value) {
                        if (value instanceof DefaultMutableTreeNode) {
                            DefaultMutableTreeNode dmtn = (DefaultMutableTreeNode) value;
                            if (dmtn.getUserObject() instanceof IMappingElement) {
                                @SuppressWarnings("unchecked")
                                IMappingElement<INode> me = (IMappingElement<INode>) dmtn.getUserObject();
                                comboBox.setSelectedItem(relationToDescription.get(getRelation(me)));
                            }
                        }
                    }

                    @Override
                    public Object getCellEditorValue() {
                        char relation = IMappingElement.EQUIVALENCE;
                        Object oo = comboBox.getSelectedItem();
                        if (oo instanceof String) {
                            String relDescr = (String) oo;
                            relation = descriptionToRelation.get(relDescr);
                        }

                        return relation;
                    }

                    @Override
                    public boolean shouldSelectCell(EventObject anEvent) {
                        if (anEvent instanceof MouseEvent) {
                            MouseEvent e = (MouseEvent) anEvent;
                            return e.getID() != MouseEvent.MOUSE_DRAGGED;
                        }
                        return true;
                    }

                    @Override
                    public boolean stopCellEditing() {
                        if (comboBox.isEditable()) {
                            // Commit edited value.
                            comboBox.actionPerformed(new ActionEvent(LinkCellEditor.this, 0, ""));
                        }
                        boolean result = super.stopCellEditing();
                        if (tree == tSource) {
                            tTarget.repaint();
                        } else if (tree == tTarget) {
                            tSource.repaint();
                        }
                        return result;

                    }
                };
                comboBox.addActionListener(delegate);
            }

            @Override
            public Component getTreeCellEditorComponent(JTree tree, Object value,
                                                        boolean isSelected,
                                                        boolean expanded,
                                                        boolean leaf, int row) {
                delegate.setValue(value);
                return editorComponent;
            }

        }

        public NodeTreeCellEditor(JTree tree, DefaultTreeCellRenderer renderer) {
            super(tree, renderer);
            comboEditor = createTreeCellComboEditor();
            oldRealEditor = realEditor;
        }

        private TreeCellEditor createTreeCellComboEditor() {
            Border aBorder = UIManager.getBorder("Tree.editorBorder");
            combo = new DefaultComboBox(aBorder);

            ComboBoxModel<String> cbm = new DefaultComboBoxModel<>(relStrings);
            combo.setModel(cbm);
            LinkCellEditor editor = new LinkCellEditor(combo) {
                public boolean shouldSelectCell(EventObject event) {
                    return super.shouldSelectCell(event);
                }
            };

            editor.setClickCountToStart(1);
            return editor;
        }

        @Override
        public boolean isCellEditable(EventObject event) {
            if (null != event) {
                if (event.getSource() instanceof JTree) {
                    if (null != lastPath) {
                        Object o = lastPath.getLastPathComponent();
                        if (o instanceof INode) {
                            realEditor = oldRealEditor;
                        } else if (o instanceof DefaultMutableTreeNode) {
                            realEditor = comboEditor;
                        }
                    }
                }
            }

            boolean result = super.isCellEditable(event);

            if (result) {
                Object node = tree.getLastSelectedPathComponent();
                result = (null != node) && ((node instanceof INode) || (node instanceof DefaultMutableTreeNode));
            }
            return result;
        }

        @Override
        public Component getTreeCellEditorComponent(JTree tree, Object value,
                                                    boolean isSelected,
                                                    boolean expanded,
                                                    boolean leaf, int row) {
            if (value instanceof INode) {
                realEditor = oldRealEditor;
            } else if (value instanceof DefaultMutableTreeNode) {
                realEditor = comboEditor;
            }

            return super.getTreeCellEditorComponent(tree, value, isSelected, expanded, leaf, row);
        }

        public void addCellEditorListener(CellEditorListener l) {
            comboEditor.addCellEditorListener(l);
            super.addCellEditorListener(l);
        }

        public void removeCellEditorListener(CellEditorListener l) {
            comboEditor.removeCellEditorListener(l);
            super.removeCellEditorListener(l);
        }

    }

    private char getRelation(IMappingElement<INode> me) {
        return mapping.getRelation(me.getSource(), me.getTarget());
    }

    private void addNode(JTree tree) {
        Object o = tree.getSelectionPath().getLastPathComponent();
        if (o instanceof INode) {
            INode node = (INode) o;
            INode parent = node.getParent();
            TreeModel m = tree.getModel();
            if (m instanceof DefaultTreeModel) {
                DefaultTreeModel dtm = (DefaultTreeModel) m;
                int nodeIdx = parent.getChildIndex(node);
                INode child = parent.createChild();
                child.nodeData().setName("New Node");
                parent.removeChild(child);
                parent.addChild(nodeIdx, child);
                dtm.nodesWereInserted(parent, new int[]{dtm.getIndexOfChild(parent, child)});
                TreePath p = createPathToRoot(child);
                tree.scrollPathToVisible(p);
                tree.setSelectionPath(p);
                tree.startEditingAtPath(p);
                recreateMapping();
            }
        }
    }

    private void addChildNode(JTree tree) {
        Object o = tree.getSelectionPath().getLastPathComponent();
        if (o instanceof INode) {
            INode node = (INode) o;
            INode child = node.createChild();
            child.nodeData().setName("New Node");
            TreeModel m = tree.getModel();
            if (m instanceof DefaultTreeModel) {
                DefaultTreeModel dtm = (DefaultTreeModel) m;
                dtm.nodesWereInserted(node, new int[]{dtm.getIndexOfChild(node, child)});
                TreePath p = createPathToRoot(child);
                tree.scrollPathToVisible(p);
                tree.setSelectionPath(p);
                tree.startEditingAtPath(p);
                recreateMapping();
            }
        }
    }

    private void deleteNode(JTree tree) {
        Object o = tree.getSelectionPath().getLastPathComponent();
        TreePath parentPath = tree.getSelectionPath().getParentPath();
        // node
        if (o instanceof INode) {
            INode node = (INode) o;
            // remove all links from this node and any node below it
            removeLinks(tree, node);
            Iterator<INode> i = node.descendantsIterator();
            while (i.hasNext()) {
                removeLinks(tree, i.next());
            }

            INode parent = node.getParent();
            TreeModel m = tree.getModel();
            if (m instanceof DefaultTreeModel) {
                DefaultTreeModel dtm = (DefaultTreeModel) m;
                int idx = dtm.getIndexOfChild(parent, node);
                parent.removeChild(node);
                dtm.nodesWereRemoved(parent, new int[]{idx}, new Object[]{node});
                if (tSource == tree) {
                    sourceModified = true;
                } else if (tTarget == tree) {
                    targetModified = true;
                }
                recreateMapping();
            }
        } else if (o instanceof DefaultMutableTreeNode) {
            // link
            DefaultMutableTreeNode dmtn = (DefaultMutableTreeNode) o;
            if (dmtn.getUserObject() instanceof IMappingElement) {
                if (null != parentPath && parentPath.getLastPathComponent() instanceof INode) {
                    // remove from own tree
                    INode parent = (INode) parentPath.getLastPathComponent();
                    if (parent.nodeData().getUserObject() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<DefaultMutableTreeNode> linkNodes = (List<DefaultMutableTreeNode>) parent.nodeData().getUserObject();
                        TreeModel m = tree.getModel();
                        if (m instanceof DefaultTreeModel) {
                            DefaultTreeModel dtm = (DefaultTreeModel) m;
                            int idx = dtm.getIndexOfChild(parent, dmtn);
                            linkNodes.remove(dmtn);
                            dtm.nodesWereRemoved(parent, new int[]{idx}, new Object[]{dmtn});
                        }
                    }

                    @SuppressWarnings("unchecked")
                    IMappingElement<INode> me = (IMappingElement<INode>) dmtn.getUserObject();
                    // remove from mapping
                    mapping.setRelation(me.getSource(), me.getTarget(), IMappingElement.IDK);
                    mappingModified = true;
                    // remove link node from the target tree
                    removeLinkFromTargetTree(tree, me);
                    if (tblMapping.getModel() instanceof MappingTableModel) {
                        MappingTableModel mtm = (MappingTableModel) tblMapping.getModel();
                        mtm.fireElementRemoved(me);
                    }
                }
            }
        }

        // select parent
        tree.setSelectionPath(parentPath);
        tree.scrollPathToVisible(parentPath);
    }

    private void closeSource() {
        source = null;
        sourceLocation = null;
        sourceModified = false;
        createTree(source, tSource, null);
        resetMappingInTable();
        setChanged();
        notifyObservers();
    }

    private void closeTarget() {
        target = null;
        targetLocation = null;
        targetModified = false;
        createTree(target, tTarget, null);
        resetMappingInTable();
        setChanged();
        notifyObservers();
    }

    private void askMappingLocation() {
        ff.setDescription(getMatchManager().getMappingRenderer().getDescription());
        getFileChooser().addChoosableFileFilter(ff);
        final int returnVal = getFileChooser().showSaveDialog(mainPanel);
        getFileChooser().removeChoosableFileFilter(ff);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            mappingLocation = getFileChooser().getSelectedFile().getAbsolutePath();
        }
    }

    private void closeMapping() {
        mapping = null;
        mappingLocation = null;
        mappingModified = false;
        createTree(source, tSource, mapping);
        createTree(target, tTarget, mapping);
        resetMappingInTable();
        pnContexts.repaint();
        setChanged();
        notifyObservers();
    }

    private void recreateMapping() {
        IContextMapping<INode> newMapping = getMatchManager().getMappingFactory().getContextMappingInstance(source, target);
        // not a nice solution, although matrix backing the mapping should be recreated anyway if a context changes
        newMapping.addAll(mapping);
        mapping = newMapping;
        resetMappingInModel(tSource);
        resetMappingInModel(tTarget);
        resetMappingInTable();
    }

    private void resetMappingInModel(JTree tree) {
        if (tree.getModel() instanceof MappingTreeModel) {
            MappingTreeModel mtm = (MappingTreeModel) tree.getModel();
            mtm.setMapping(mapping);
        }
    }

    private void removeLinks(final JTree tree, final INode node) {
        // does not delete the links themselves, because it is called from the deleteNode, which
        // deletes the container node anyway
        Set<IMappingElement<INode>> links;
        if (null != mapping) {
            if (tree == tSource) {
                links = mapping.getSources(node);
            } else {
                links = mapping.getTargets(node);
            }
            MappingTableModel mtm = null;
            if (tblMapping.getModel() instanceof MappingTableModel) {
                mtm = (MappingTableModel) tblMapping.getModel();
            }
            for (IMappingElement<INode> me : links) {
                mapping.setRelation(me.getSource(), me.getTarget(), IMappingElement.IDK);
                mappingModified = true;
                removeLinkFromTargetTree(tree, me);
                if (null != mtm) {
                    mtm.fireElementRemoved(me);
                }
            }
        }
    }

    private void removeLinkFromTargetTree(final JTree tree, final IMappingElement<INode> me) {
        JTree oppositeTree = getOppositeTree(tree);
        INode targetNode = getTargetNode(tree, me);
        if (targetNode.nodeData().getUserObject() instanceof List) {
            @SuppressWarnings("unchecked")
            List<DefaultMutableTreeNode> targetLinkNodes = (List<DefaultMutableTreeNode>) targetNode.nodeData().getUserObject();
            DefaultMutableTreeNode targetLinkNodeToDelete = null;
            for (DefaultMutableTreeNode targetLinkNode : targetLinkNodes) {
                // if this is the same link we're deleting in the source, delete it from the target too
                if (targetLinkNode.getUserObject() instanceof IMappingElement) {
                    @SuppressWarnings("unchecked")
                    IMappingElement<INode> targetME = (IMappingElement<INode>) targetLinkNode.getUserObject();
                    if (targetME.equals(me)) {
                        targetLinkNodeToDelete = targetLinkNode;
                        break;
                    }
                }
            }//for
            if (null != targetLinkNodeToDelete) {
                if (oppositeTree.getModel() instanceof MappingTreeModel) {
                    MappingTreeModel mtm = (MappingTreeModel) oppositeTree.getModel();
                    int idx = mtm.getIndexOfChild(targetNode, targetLinkNodeToDelete);
                    targetLinkNodes.remove(targetLinkNodeToDelete);
                    if (!mtm.isCoalescedInAnyParent(targetNode)) {
                        mtm.nodesWereRemoved(targetNode, new int[]{idx}, new Object[]{targetLinkNodeToDelete});
                    }
                }
            } else {
                log.error("Cannot find symmetric link while deleting");
            }
        }
    }

    private INode getTargetNode(JTree tree, IMappingElement<INode> me) {
        if (tree == tSource) {
            return me.getTarget();
        } else if (tree == tTarget) {
            return me.getSource();
        } else {
            return null;
        }
    }

    private JTree getOppositeTree(final JTree tree) {
        if (tree == tSource) {
            return tTarget;
        } else if (tree == tTarget) {
            return tSource;
        } else {
            return null;
        }
    }

    public static void uncoalesceNode(JTree tree) {
        Object o = tree.getSelectionPath().getLastPathComponent();
        TreePath parentPath = tree.getSelectionPath().getParentPath();
        if (o instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode dmtn = (DefaultMutableTreeNode) o;
            if (dmtn.getUserObject() instanceof BaseCoalesceTreeModel.Coalesce) {
                if (null != parentPath && parentPath.getLastPathComponent() instanceof IBaseNode) {
                    IBaseNode parent = (IBaseNode) parentPath.getLastPathComponent();
                    TreeModel m = tree.getModel();
                    if (m instanceof BaseCoalesceTreeModel) {
                        BaseCoalesceTreeModel mtm = (BaseCoalesceTreeModel) m;
                        mtm.uncoalesce(parent);
                    }
                }
            }
        }

        // select parent
        tree.setSelectionPath(parentPath);
        tree.scrollPathToVisible(parentPath);
    }

    public static void uncoalesceTree(JTree tree) {
        if (tree.getModel() instanceof BaseCoalesceTreeModel) {
            BaseCoalesceTreeModel mtm = (BaseCoalesceTreeModel) tree.getModel();
            mtm.uncoalesceAll();
        }
    }

    private void openSource(final File file) {
        SwingWorker<IContext, INode> sourceLoadingTask =
                createContextLoadingTask(file, semSource, pbSourceProgress, true);
        sourceLoadingTask.execute();
    }

    private void openTarget(final File file) {
        SwingWorker<IContext, INode> targetLoadingTask =
                createContextLoadingTask(file, semTarget, pbTargetProgress, false);
        targetLoadingTask.execute();
    }

    private void saveSource(final String location) {
        SwingWorker<Void, INode> sourceRenderingTask =
                createContextRenderingTask(source, location, semSource, pbSourceProgress, true);
        sourceRenderingTask.execute();
    }

    private void saveTarget(final String location) {
        SwingWorker<Void, INode> targetRenderingTask =
                createContextRenderingTask(target, location, semTarget, pbTargetProgress, false);
        targetRenderingTask.execute();
    }

    private void createLoadingTree(JTree tree) {
        createTree(null, tree, null);
        tree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode(LOADING_LABEL)));
    }

    private SwingWorker<IContext, INode> createContextLoadingTask(final File file,
                                                                  final Semaphore semContext,
                                                                  final JProgressBar pbContext,
                                                                  final boolean processingSource) {

        pbContext.setIndeterminate(true);

        final AsyncTask<IContext, INode> loadingTask;
        if (getMatchManager().getContextLoader() instanceof IAsyncContextLoader) {
            IAsyncContextLoader acl = (IAsyncContextLoader) getMatchManager().getContextLoader();
            loadingTask = acl.asyncLoad(file.getAbsolutePath());
            setupTaskProgressBarAndHandler(loadingTask, pbContext);

        } else {
            loadingTask = null;
        }

        SwingWorker<IContext, INode> task = new SwingWorker<IContext, INode>() {
            @Override
            protected IContext doInBackground() throws Exception {
                semManager.acquire();
                semContext.acquire(PERMITS);
                semMapping.acquire();
                if (null != loadingTask) {
                    loadingTask.execute();
                    return loadingTask.get();
                } else {
                    return (IContext) getMatchManager().loadContext(file.getAbsolutePath());
                }
            }

            @Override
            protected void done() {
                super.done();
                if (!isCancelled()) {
                    try {
                        if (processingSource) {
                            source = get();
                            log.info("Opened source");
                        } else {
                            target = get();
                            log.info("Opened target");
                        }
                        if (null != source && null != target) {
                            mapping = getMatchManager().getMappingFactory().getContextMappingInstance(source, target);
                        }
                        if (!processingSource && null != source) {
                            resetMappingInModel(tSource);
                            resetMappingInTable();
                        }
                        if (processingSource && null != target) {
                            resetMappingInModel(tTarget);
                            resetMappingInTable();
                        }
                        if (processingSource) {
                            sourceLocation = file.getAbsolutePath();
                        } else {
                            targetLocation = file.getAbsolutePath();
                        }
                    } catch (InterruptedException e) {
                        // ok, leave source as it was
                    } catch (ExecutionException e) {
                        if (log.isEnabledFor(Level.ERROR)) {
                            log.error("Error while loading context from " + file.getAbsolutePath(), e.getCause());
                        }
                        JOptionPane.showMessageDialog(frame, "Error occurred while loading the context from " + file.getAbsolutePath() + "\n\n" + e.getCause().getMessage() + "\n\nPlease, ensure the file exists and its format is correct.", "Context loading error", JOptionPane.ERROR_MESSAGE);
                    }
                }

                // changes "loading..." to the tree
                if (processingSource) {
                    createTree(source, tSource, mapping);
                } else {
                    createTree(target, tTarget, mapping);
                }
                pbContext.setVisible(false);
                semMapping.release();
                semContext.release(PERMITS);
                semManager.release();
                setChanged();
                notifyObservers();
            }
        };
        task.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ("state".equals(evt.getPropertyName())) {
                    SwingWorker.StateValue oldState = (SwingWorker.StateValue) evt.getOldValue();
                    SwingWorker.StateValue newState = (SwingWorker.StateValue) evt.getNewValue();
                    if (SwingWorker.StateValue.PENDING == oldState && SwingWorker.StateValue.STARTED == newState) {
                        if (processingSource) {
                            log.info("Opening source: " + file.getAbsolutePath());
                            createLoadingTree(tSource);
                        } else {
                            log.info("Opening target: " + file.getAbsolutePath());
                            createLoadingTree(tTarget);
                        }

                        pbContext.setVisible(true);

                        setChanged();
                        notifyObservers();
                    }
                    handleUITaskCompletion(oldState, newState, pbContext);
                }
            }
        });
        return task;
    }

    private SwingWorker<Void, INode> createContextRenderingTask(final IContext context, final String location,
                                                                final Semaphore semContext,
                                                                final JProgressBar pbContext,
                                                                final boolean processingSource) {
        pbContext.setIndeterminate(true);

        final AsyncTask<Void, INode> renderingTask;
        if (getMatchManager().getContextRenderer() instanceof IAsyncContextRenderer) {
            IAsyncContextRenderer acl = (IAsyncContextRenderer) getMatchManager().getContextRenderer();
            renderingTask = acl.asyncRender(context, location);
            setupTaskProgressBarAndHandler(renderingTask, pbContext);
        } else {
            renderingTask = null;
        }

        SwingWorker<Void, INode> task = new SwingWorker<Void, INode>() {
            @Override
            protected Void doInBackground() throws Exception {
                semManager.acquire();
                semContext.acquire(PERMITS);
                semMapping.acquire();
                if (null != renderingTask) {
                    renderingTask.execute();
                    renderingTask.get();
                } else {
                    getMatchManager().renderContext(context, location);
                }
                return null;
            }

            @Override
            protected void done() {
                super.done();
                if (!isCancelled()) {
                    try {
                        get();
                        if (processingSource) {
                            sourceModified = false;
                            log.info("Saved source");
                        } else {
                            targetModified = false;
                            log.info("Saved target");
                        }
                    } catch (InterruptedException e) {
                        // ok, leave source as it was
                    } catch (ExecutionException e) {
                        if (log.isEnabledFor(Level.ERROR)) {
                            log.error("Error while rendering context to " + location, e.getCause());
                        }
                        JOptionPane.showMessageDialog(frame, "Error occurred while rendering context to " + location + "\n\n" + e.getCause().getMessage() + "\n\nPlease, ensure the S-Match is intact, configured properly and try again.", "Context rendering error", JOptionPane.ERROR_MESSAGE);
                    }
                }

                pbContext.setVisible(false);
                semMapping.release();
                semContext.release(PERMITS);
                semManager.release();
                setChanged();
                notifyObservers();
            }
        };
        task.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ("state".equals(evt.getPropertyName())) {
                    SwingWorker.StateValue oldState = (SwingWorker.StateValue) evt.getOldValue();
                    SwingWorker.StateValue newState = (SwingWorker.StateValue) evt.getNewValue();
                    if (SwingWorker.StateValue.PENDING == oldState && SwingWorker.StateValue.STARTED == newState) {
                        if (processingSource) {
                            log.info("Saving source: " + location);
                        } else {
                            log.info("Saving target: " + location);
                        }

                        pbContext.setVisible(true);

                        setChanged();
                        notifyObservers();
                    }
                    handleUITaskCompletion(oldState, newState, pbContext);
                }
            }
        });
        return task;
    }

    private SwingWorker<Void, Void> createContextOfflineTask(final IContext context,
                                                             final Semaphore semContext,
                                                             final JProgressBar pbContext,
                                                             final boolean processingSource) {
        pbContext.setIndeterminate(true);

        final AsyncTask<Void, INode> offlineTask;
        if (getMatchManager() instanceof IAsyncMatchManager) {
            IAsyncMatchManager amm = (IAsyncMatchManager) getMatchManager();
            offlineTask = amm.asyncOffline(context);
            setupTaskProgressBarAndHandler(offlineTask, pbContext);
        } else {
            offlineTask = null;
        }
        SwingWorker<Void, Void> task = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                semManager.acquire();
                semContext.acquire(PERMITS);
                semMapping.acquire();
                if (null != offlineTask) {
                    offlineTask.execute();
                    offlineTask.get();
                } else {
                    getMatchManager().offline(context);
                }
                return null;
            }

            @Override
            protected void done() {
                super.done();
                if (!isCancelled()) {
                    try {
                        get();
                        if (processingSource) {
                            log.info("Preprocessed source");
                            sourceModified = true;
                        } else {
                            log.info("Preprocessed target");
                            targetModified = true;
                        }
                    } catch (InterruptedException e) {
                        // ok, leave source as it was
                    } catch (ExecutionException e) {
                        if (log.isEnabledFor(Level.ERROR)) {
                            log.error("Error while preprocessing context: " + e.getCause().getMessage(), e.getCause());
                        }
                        JOptionPane.showMessageDialog(frame, "Error occurred while preprocessing context:\n\n" + e.getCause().getMessage()
                                        + "\n\nPlease, ensure the S-Match is intact, configured properly and try again.",
                                "Context rendering error", JOptionPane.ERROR_MESSAGE);
                    }
                }

                pbContext.setVisible(false);
                semMapping.release();
                semContext.release(PERMITS);
                semManager.release();
                setChanged();
                notifyObservers();
            }
        };
        task.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ("state".equals(evt.getPropertyName())) {
                    SwingWorker.StateValue oldState = (SwingWorker.StateValue) evt.getOldValue();
                    SwingWorker.StateValue newState = (SwingWorker.StateValue) evt.getNewValue();
                    if (SwingWorker.StateValue.PENDING == oldState && SwingWorker.StateValue.STARTED == newState) {
                        if (processingSource) {
                            log.info("Preprocessing source...");
                        } else {
                            log.info("Preprocessing target...");
                        }

                        pbContext.setVisible(true);

                        setChanged();
                        notifyObservers();
                    }
                    handleUITaskCompletion(oldState, newState, pbContext);
                }
            }
        });
        return task;
    }

    private SwingWorker<IContextMapping<INode>, IMappingElement<INode>> createMatchTask() {
        final SwingWorker<Void, Void> sourcePreprocess;
        final SwingWorker<Void, Void> targetPreprocess;

        // prepare the match task
        pbProgress.setIndeterminate(true);
        final AsyncTask<IContextMapping<INode>, IMappingElement<INode>> matchTask;
        if (getMatchManager() instanceof IAsyncMatchManager) {
            IAsyncMatchManager amm = (IAsyncMatchManager) getMatchManager();
            matchTask = amm.asyncOnline(source, target);
            setupTaskProgressBarAndHandler(matchTask, pbProgress);
        } else {
            matchTask = null;
        }
        
        if (!source.getRoot().nodeData().isSubtreePreprocessed()) {
            sourcePreprocess = createContextOfflineTask(source, semSource, pbSourceProgress, true);
        } else {
            sourcePreprocess = null;
        }
        if (!target.getRoot().nodeData().isSubtreePreprocessed()) {
            targetPreprocess = createContextOfflineTask(target, semTarget, pbTargetProgress, false);
        } else {
            targetPreprocess = null;
        }

        SwingWorker<IContextMapping<INode>, IMappingElement<INode>> task =
                new SwingWorker<IContextMapping<INode>, IMappingElement<INode>>() {
                    @Override
                    public IContextMapping<INode> doInBackground() throws Exception {
                        if (null != matchTask) {
                            // parallel execution of source and target preprocess
                            if (null != sourcePreprocess) {
                                sourcePreprocess.execute();
                            }
                            if (null != targetPreprocess) {
                                targetPreprocess.execute();
                            }
                            if (null != sourcePreprocess) {
                                sourcePreprocess.get();
                            }
                            if (null != targetPreprocess) {
                                targetPreprocess.get();
                            }
                        } else {
                            // serial execution of source and target preprocess
                            if (null != sourcePreprocess) {
                                sourcePreprocess.execute();
                                sourcePreprocess.get();
                            }
                            if (null != targetPreprocess) {
                                targetPreprocess.execute();
                                targetPreprocess.get();
                            }
                        }

                        semManager.acquire();
                        semSource.acquire();
                        semTarget.acquire();
                        semMapping.acquire(PERMITS);
                        if ((null == sourcePreprocess || !sourcePreprocess.isCancelled()) &&
                                (null == targetPreprocess || !targetPreprocess.isCancelled())) {
                            if (null != matchTask) {
                                matchTask.execute();
                                IContextMapping<INode> result = matchTask.get();
                                if (matchTask.isCancelled()) {
                                    cancel(true);
                                }
                                return result;
                            } else {
                                return getMatchManager().online(source, target);
                            }
                        } else {
                            // some preprocessing was cancelled
                            cancel(true);
                            return null;
                        }
                    }

                    @Override
                    public void done() {
                        if (!isCancelled()) {
                            try {
                                mapping = get();
                            } catch (InterruptedException e) {
                                // ok, leave source as it was
                            } catch (ExecutionException e) {
                                if (log.isEnabledFor(Level.ERROR)) {
                                    log.error("Error while creating a mapping between source and target contexts", e.getCause());
                                }
                                JOptionPane.showMessageDialog(frame, "Error occurred while creating the mapping:\n\n" + e.getCause().getMessage()
                                                + "\n\nPlease, ensure the S-Match is intact, configured properly and try again.",
                                        "Mapping creation error", JOptionPane.ERROR_MESSAGE);
                            }
                        }

                        resetMappingInTable();
                        createTree(source, tSource, mapping);
                        createTree(target, tTarget, mapping);
                        mappingLocation = null;
                        mappingModified = true;

                        pbProgress.setVisible(false);
                        semMapping.release(PERMITS);
                        semTarget.release();
                        semSource.release();
                        semManager.release();
                        setChanged();
                        notifyObservers();
                    }
                };
        task.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ("state".equals(evt.getPropertyName())) {
                    SwingWorker.StateValue oldState = (SwingWorker.StateValue) evt.getOldValue();
                    SwingWorker.StateValue newState = (SwingWorker.StateValue) evt.getNewValue();
                    if (SwingWorker.StateValue.PENDING == oldState && SwingWorker.StateValue.STARTED == newState) {
                        pbProgress.setVisible(true);

                        setChanged();
                        notifyObservers();
                    }
                    handleUITaskCompletion(oldState, newState, pbProgress);
                }
            }
        });
        return task;
    }

    private void openMapping(final File file) {
        pbProgress.setIndeterminate(true);

        final AsyncTask<IContextMapping<INode>, IMappingElement<INode>> loadingTask;
        if (getMatchManager().getMappingLoader() instanceof IAsyncMappingLoader) {
            IAsyncMappingLoader acl = (IAsyncMappingLoader) getMatchManager().getMappingLoader();
            loadingTask = acl.asyncLoad(source, target, file.getAbsolutePath());
            setupTaskProgressBarAndHandler(loadingTask, pbProgress);
        } else {
            loadingTask = null;
        }

        SwingWorker<IContextMapping<INode>, IMappingElement<INode>> task =
                new SwingWorker<IContextMapping<INode>, IMappingElement<INode>>() {
                    @Override
                    protected IContextMapping<INode> doInBackground() throws Exception {
                        semManager.acquire();
                        semSource.acquire();
                        semTarget.acquire();
                        semMapping.acquire(PERMITS);
                        if (null != loadingTask) {
                            loadingTask.execute();
                            return loadingTask.get();
                        } else {
                            return getMatchManager().loadMapping(source, target, file.getAbsolutePath());
                        }
                    }

                    @Override
                    protected void done() {
                        super.done();
                        if (!isCancelled()) {
                            try {
                                mapping = get();
                                mappingLocation = file.getAbsolutePath();

                                createTree(source, tSource, mapping);
                                createTree(target, tTarget, mapping);
                            } catch (InterruptedException e) {
                                // ok, leave it as it was
                            } catch (ExecutionException e) {
                                if (log.isEnabledFor(Level.ERROR)) {
                                    log.error("Error while loading mapping from " + file.getAbsolutePath(), e.getCause());
                                }
                                JOptionPane.showMessageDialog(frame, "Error occurred while loading the mapping from " + file.getAbsolutePath() + "\n\n" + e.getCause().getMessage() + "\n\nPlease, ensure the file exists and its format is correct.", "Mapping loading error", JOptionPane.ERROR_MESSAGE);
                            }
                        }

                        // changes "loading..." to the new one or the old one
                        resetMappingInTable();
                        pnContexts.repaint();

                        pbProgress.setVisible(false);
                        semMapping.release(PERMITS);
                        semTarget.release();
                        semSource.release();
                        semManager.release();
                        setChanged();
                        notifyObservers();
                    }
                };
        task.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ("state".equals(evt.getPropertyName())) {
                    SwingWorker.StateValue oldState = (SwingWorker.StateValue) evt.getOldValue();
                    SwingWorker.StateValue newState = (SwingWorker.StateValue) evt.getNewValue();
                    if (SwingWorker.StateValue.PENDING == oldState && SwingWorker.StateValue.STARTED == newState) {
                        log.info("Opening mapping: " + file.getAbsolutePath());
                        if (!(getMatchManager().getMappingLoader() instanceof IAsyncMappingLoader)) {
                            pbProgress.setIndeterminate(true);
                        }
                        pbProgress.setVisible(true);

                        tblMapping.setModel(new DefaultTableModel(
                                new Vector<>(Collections.emptyList()),
                                new Vector<>(Arrays.asList(LOADING_LABEL))));

                        setChanged();
                        notifyObservers();
                    }
                    handleUITaskCompletion(oldState, newState, pbProgress);
                }
            }
        });
        task.execute();
    }

    private void saveMapping() {
        pbProgress.setIndeterminate(true);

        final AsyncTask<Void, IMappingElement<INode>> savingTask;
        if (getMatchManager().getMappingRenderer() instanceof IAsyncMappingRenderer) {
            IAsyncMappingRenderer acl = (IAsyncMappingRenderer) getMatchManager().getMappingRenderer();
            savingTask = acl.asyncRender(mapping, mappingLocation);
            setupTaskProgressBarAndHandler(savingTask, pbProgress);
        } else {
            savingTask = null;
        }

        SwingWorker<Void, IMappingElement<INode>> task = new SwingWorker<Void, IMappingElement<INode>>() {
            @Override
            protected Void doInBackground() throws Exception {
                semManager.acquire();
                semSource.acquire();
                semTarget.acquire();
                semMapping.acquire(PERMITS);
                if (null != savingTask) {
                    savingTask.execute();
                    savingTask.get();
                } else {
                    getMatchManager().renderMapping(mapping, mappingLocation);
                }
                return null;
            }

            @Override
            protected void done() {
                super.done();
                if (!isCancelled()) {
                    try {
                        get();
                        mappingModified = false;
                    } catch (InterruptedException e) {
                        // ok, leave it as it was
                    } catch (ExecutionException e) {
                        if (log.isEnabledFor(Level.ERROR)) {
                            log.error("Error while saving mapping to " + mappingLocation, e.getCause());
                        }
                        JOptionPane.showMessageDialog(frame, "Error occurred while saving the mapping to " + mappingLocation + "\n\n" + e.getCause().getMessage() + "\n\nPlease, ensure the S-Match is intact, configured properly and try again.", "Mapping saving error", JOptionPane.ERROR_MESSAGE);
                    }
                }

                pbProgress.setVisible(false);
                semMapping.release(PERMITS);
                semTarget.release();
                semSource.release();
                semManager.release();
                setChanged();
                notifyObservers();
            }
        };
        task.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ("state".equals(evt.getPropertyName())) {
                    SwingWorker.StateValue oldState = (SwingWorker.StateValue) evt.getOldValue();
                    SwingWorker.StateValue newState = (SwingWorker.StateValue) evt.getNewValue();
                    if (SwingWorker.StateValue.PENDING == oldState && SwingWorker.StateValue.STARTED == newState) {
                        log.info("Saving mapping: " + mappingLocation);
                        if (!(getMatchManager().getMappingLoader() instanceof IAsyncMappingLoader)) {
                            pbProgress.setIndeterminate(true);
                        }
                        pbProgress.setVisible(true);

                        setChanged();
                        notifyObservers();
                    }
                    handleUITaskCompletion(oldState, newState, pbProgress);
                }
            }
        });
        task.execute();
    }

    private void setupTaskProgressBarAndHandler(final AsyncTask asyncTask, final JProgressBar progressBar) {
        if (0 < asyncTask.getTotal()) {
            progressBar.setIndeterminate(false);
            progressBar.setMaximum(100);
            progressBar.setMinimum(0);
            progressBar.setValue(0);

            // set up progress handler
            asyncTask.addPropertyChangeListener(new PropertyChangeListener() {
                private final long total = asyncTask.getTotal();

                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if ("progress".equals(evt.getPropertyName())) {
                        Long progress = (Long) evt.getNewValue();
                        progressBar.setValue((int) (100 * (progress / (double) total)));
                    }
                }
            });
        }
    }

    private void handleUITaskCompletion(SwingWorker.StateValue oldState, SwingWorker.StateValue newState, JProgressBar pbContext) {
        if (SwingWorker.StateValue.STARTED == oldState && SwingWorker.StateValue.DONE == newState) {
            pbContext.setIndeterminate(false);
            pbContext.setVisible(false);

            setChanged();
            notifyObservers();
        }
    }

    public static TreePath createPathToRoot(IBaseNode node) {
        Deque<IBaseNode> pathToRoot = new ArrayDeque<>();
        IBaseNode curNode = node;
        while (null != curNode) {
            pathToRoot.push(curNode);
            curNode = curNode.getParent();
        }
        TreePath pp = new TreePath(pathToRoot.pop());
        while (!pathToRoot.isEmpty()) {
            pp = pp.pathByAddingChild(pathToRoot.pop());
        }
        return pp;
    }

    public void update(Observable o, Object arg) {
        //update source location
        String sourceLocationText = sourceLocation;
        if (null == sourceLocationText) {
            sourceLocationText = "unnamed";
        }
        if (sourceModified) {
            sourceLocationText = sourceLocationText + " *";
        }
        teSourceContextLocation.setText(sourceLocationText);
        teSourceContextLocation.setToolTipText(sourceLocationText);

        //update target location
        String targetLocationText = targetLocation;
        if (null == targetLocationText) {
            targetLocationText = "unnamed";
        }
        if (targetModified) {
            targetLocationText = targetLocationText + " *";
        }
        teTargetContextLocation.setText(targetLocationText);
        teTargetContextLocation.setToolTipText(targetLocationText);

        //update mapping location
        String mappingLocationText = mappingLocation;
        if (null == mappingLocationText) {
            mappingLocationText = "unnamed";
        }
        if (mappingModified) {
            mappingLocationText = mappingLocationText + " *";
        }
        teMappingLocation.setText(mappingLocationText);
        teMappingLocation.setToolTipText(mappingLocationText);

        tSource.setEditable(PERMITS == semSource.availablePermits());
        tTarget.setEditable(PERMITS == semTarget.availablePermits());
    }

    private void buildMenu() {
        mainMenu = new JMenuBar();

        JMenu jmSource = new JMenu("Source");
        jmSource.setMnemonic('S');
        jmSource.add(acSourceCreate);
        jmSource.addSeparator();
        jmSource.add(acSourceAddNode);
        jmSource.add(acSourceAddChildNode);
        jmSource.add(acSourceDelete);
        jmSource.addSeparator();
        jmSource.add(acSourceUncoalesce);
        jmSource.add(acSourceUncoalesceAll);
        jmSource.addSeparator();
        jmSource.add(acSourceOpen);
        jmSource.add(acSourcePreprocess);
        jmSource.add(acSourceClose);
        jmSource.add(acSourceSave);
        jmSource.add(acSourceSaveAs);
        mainMenu.add(jmSource);

        JMenu jmTarget = new JMenu("Target");
        jmTarget.setMnemonic('T');
        jmTarget.add(acTargetCreate);
        jmTarget.addSeparator();
        jmTarget.add(acTargetAddNode);
        jmTarget.add(acTargetAddChildNode);
        jmTarget.add(acTargetDelete);
        jmTarget.addSeparator();
        jmTarget.add(acTargetUncoalesce);
        jmTarget.add(acTargetUncoalesceAll);
        jmTarget.addSeparator();
        jmTarget.add(acTargetOpen);
        jmTarget.add(acTargetPreprocess);
        jmTarget.add(acTargetClose);
        jmTarget.add(acTargetSave);
        jmTarget.add(acTargetSaveAs);
        mainMenu.add(jmTarget);

        JMenu jmMapping = new JMenu("Mapping");
        jmMapping.setMnemonic('M');
        jmMapping.add(acMappingCreate);
        jmMapping.add(acMappingOpen);
        jmMapping.add(acMappingClose);
        jmMapping.add(acMappingSave);
        jmMapping.add(acMappingSaveAs);
        mainMenu.add(jmMapping);

        JMenu jmEdit = new JMenu("Edit");
        jmEdit.setMnemonic('E');
        jmEdit.add(acEditAddNode);
        jmEdit.add(acEditAddChildNode);
        jmEdit.add(acEditAddLink);
        jmEdit.add(acEditDelete);
        mainMenu.add(jmEdit);

        JMenu jmView = new JMenu("View");
        jmMapping.setMnemonic('V');
        final Action acViewClearLog = new ActionViewClearLog();
        jmView.add(acViewUncoalesce);
        jmView.add(acViewUncoalesceAll);
        jmView.addSeparator();
        jmView.add(acViewClearLog);
        mainMenu.add(jmView);

        JMenu jmOptions = new JMenu("Options");
        jmOptions.setMnemonic('O');
        jmOptions.add(acConfigurationEdit);
        mainMenu.add(jmOptions);

        JMenu jmHelp = new JMenu("Help");
        jmHelp.setMnemonic('H');
        jmHelp.add(new ActionBrowseURL("https://github.com/s-match/s-match-core/wiki", "Open S-Match Documentation..."));
        jmHelp.add(new ActionBrowseURL("http://sourceforge.net/projects/s-match/", "Open S-Match project web site..."));
        jmHelp.add(new ActionBrowseURL("http://semanticmatching.org/", "Open SemanticMatching.org web site..."));
        mainMenu.add(jmHelp);
    }

    private void buildStaticGUI() {
        log.info("Building the GUI...");
        acSourceCreate = new ActionSourceCreate();
        acSourceOpen = new ActionSourceOpen();
        acSourcePreprocess = new ActionSourcePreprocess();
        acSourceClose = new ActionSourceClose();
        acSourceSave = new ActionSourceSave();
        acSourceSaveAs = new ActionSourceSaveAs();

        acTargetCreate = new ActionTargetCreate();
        acTargetOpen = new ActionTargetOpen();
        acTargetPreprocess = new ActionTargetPreprocess();
        acTargetClose = new ActionTargetClose();
        acTargetSave = new ActionTargetSave();
        acTargetSaveAs = new ActionTargetSaveAs();

        acMappingCreate = new ActionMappingCreate();
        acMappingOpen = new ActionMappingOpen();
        acMappingClose = new ActionMappingClose();
        acMappingSave = new ActionMappingSave();
        acMappingSaveAs = new ActionMappingSaveAs();

        acEditAddNode = new ActionEditAddNode();
        acEditAddChildNode = new ActionEditAddChildNode();
        acEditAddLink = new ActionEditAddLink();
        acEditDelete = new ActionEditDelete();

        acViewUncoalesce = new ActionViewUncoalesce();
        acViewUncoalesceAll = new ActionViewUncoalesceAll();

        acConfigurationEdit = new ActionConfigurationEdit();

        log.debug("Built actions");
        String layoutColumns = "fill:default:grow";
        String layoutRows = "top:d:noGrow,top:4dlu:noGrow,top:d:noGrow,top:4dlu:noGrow,fill:max(d;100px):grow,bottom:2dlu:noGrow,bottom:d:noGrow";

        FormLayout layout = new FormLayout(layoutColumns, layoutRows);
        //PanelBuilder builder = new PanelBuilder(layout, new FormDebugPanel());
        PanelBuilder builder = new PanelBuilder(layout);
        //builder.setDefaultDialogBorder();
        CellConstraints cc = new CellConstraints();

        //build main toolbar
        JToolBar tbMain = new JToolBar();
        tbMain.setFloatable(false);
        builder.add(tbMain, cc.xy(1, 1, CellConstraints.FILL, CellConstraints.DEFAULT));
        final JLabel lbMapping = new JLabel();
        lbMapping.setText("Mapping:  ");
        tbMain.add(lbMapping);
        JButton btMappingOpen = new JButton(acMappingOpen);
        btMappingOpen.setHideActionText(true);
        JButton btMappingSave = new JButton(acMappingSave);
        btMappingSave.setHideActionText(true);
        tbMain.add(btMappingOpen);
        tbMain.add(btMappingSave);
        tbMain.addSeparator();
        JButton btEditAddLink = new JButton(acEditAddLink);
        btEditAddLink.setHideActionText(true);
        tbMain.add(btEditAddLink);
        final JLabel lbConfig = new JLabel();
        lbConfig.setText("    Config:  ");
        tbMain.add(lbConfig);
        cbConfig = new JComboBox<>();
        cmConfigs = new DefaultComboBoxModel<>();
        // read config files
        File f = new File(GUI_CONF_FILE);
        File configFolder = f.getParentFile();
        String[] configFiles = configFolder.list(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".xml") && name.startsWith("s-match");
            }
        });
        for (String config : configFiles) {
            cmConfigs.addElement(config);
        }
        String configName = (new File(configFileName)).getName();
        int defConfigIndex = cmConfigs.getIndexOf(configName);
        if (-1 != defConfigIndex) {
            cmConfigs.setSelectedItem(cmConfigs.getElementAt(defConfigIndex));
            cbConfigPrevIndex = defConfigIndex;
        }
        cbConfig.setModel(cmConfigs);
        cbConfig.addItemListener(configComboListener);
        tbMain.add(cbConfig);

        // build mapping location field
        teMappingLocation = new JTextField();
        teMappingLocation.setEnabled(false);
        teMappingLocation.setHorizontalAlignment(JTextField.RIGHT);
        ToolTipManager.sharedInstance().registerComponent(teMappingLocation);
        builder.add(teMappingLocation, cc.xy(1, 3, CellConstraints.FILL, CellConstraints.FILL));
        log.debug("Built toolbars");


        //build trees panel
        spnContextsLog = new JSplitPane();
        spnContextsLog.setContinuousLayout(true);
        spnContextsLog.setOrientation(JSplitPane.VERTICAL_SPLIT);
        spnContextsLog.setOneTouchExpandable(true);

        builder.add(spnContextsLog, cc.xy(1, 5, CellConstraints.DEFAULT, CellConstraints.FILL));

        //split pane for context above and mapping table below
        spnContextsMapping = new JSplitPane();
        spnContextsMapping.setContinuousLayout(true);
        spnContextsMapping.setOrientation(JSplitPane.VERTICAL_SPLIT);
        spnContextsMapping.setOneTouchExpandable(true);
        pnContextsMapping = new JPanel();
        pnContextsMapping.setLayout(new FormLayout("fill:d:grow", "fill:d:grow"));
        pnContextsMapping.add(spnContextsMapping, cc.xy(1, 1));

        spnContexts = new JSplitPane();
        pnContexts = new JPanel();
        pnContexts.setLayout(new FormLayout("fill:d:grow", "fill:d:grow"));

        spnContextsMapping.setTopComponent(pnContexts);

        spnContextsLog.setTopComponent(pnContextsMapping);
        pnContexts.add(spnContexts, cc.xy(1, 1));

        //build source
        FormLayout pnSourceLayout = new FormLayout("fill:d:grow", "center:d:noGrow,top:4dlu:noGrow,center:d:noGrow,top:4dlu:noGrow,center:d:grow,bottom:2dlu:noGrow,bottom:d:noGrow");
//        PanelBuilder pnSourceBuilder = new PanelBuilder(pnSourceLayout, new FormDebugPanel());
        PanelBuilder pnSourceBuilder = new PanelBuilder(pnSourceLayout);
        JToolBar tbSource = new JToolBar();
        tbSource.setFloatable(false);
        pnSourceBuilder.add(tbSource, cc.xy(1, 1, CellConstraints.FILL, CellConstraints.DEFAULT));
        JButton btSourceCreate = new JButton(acSourceCreate);
        btSourceCreate.setHideActionText(true);
        tbSource.add(btSourceCreate);
        tbSource.addSeparator();
        JButton btSourceOpen = new JButton(acSourceOpen);
        btSourceOpen.setHideActionText(true);
        JButton btSourceSave = new JButton(acSourceSave);
        btSourceSave.setHideActionText(true);
        tbSource.add(btSourceOpen);
        tbSource.add(btSourceSave);
        teSourceContextLocation = new JTextField();
        teSourceContextLocation.setEnabled(false);
        teSourceContextLocation.setHorizontalAlignment(JTextField.RIGHT);
        pnSourceBuilder.add(teSourceContextLocation, cc.xy(1, 3, CellConstraints.FILL, CellConstraints.FILL));
        ToolTipManager.sharedInstance().registerComponent(teSourceContextLocation);
        spSource = new JScrollPane();
        pnSourceBuilder.add(spSource, cc.xy(1, 5, CellConstraints.FILL, CellConstraints.FILL));
        tSource = new JTree(new DefaultMutableTreeNode(EMPTY_ROOT_NODE_LABEL));
        ToolTipManager.sharedInstance().registerComponent(tSource);
        tSource.addMouseListener(treeMouseListener);
        tSource.addMouseListener(treePopupListener);
        tSource.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        spSource.setViewportView(tSource);
        tbSource.addSeparator();
        acSourceAddNode = new ActionEditAddNode(tSource);
        acSourceAddChildNode = new ActionEditAddChildNode(tSource);
        acSourceDelete = new ActionEditDelete(tSource);
        JButton btSourceEditAddNode = new JButton(acSourceAddNode);
        JButton btSourceEditAddChildNode = new JButton(acSourceAddChildNode);
        JButton btSourceEditDelete = new JButton(acSourceDelete);
        btSourceEditAddNode.setHideActionText(true);
        btSourceEditAddChildNode.setHideActionText(true);
        btSourceEditDelete.setHideActionText(true);
        tbSource.add(btSourceEditAddNode);
        tbSource.add(btSourceEditAddChildNode);
        tbSource.add(btSourceEditDelete);
        acSourceUncoalesce = new ActionViewUncoalesce(tSource);
        acSourceUncoalesceAll = new ActionViewUncoalesceAll(tSource);
        JButton btSourceUncoalesceAll = new JButton(acSourceUncoalesceAll);
        btSourceUncoalesceAll.setHideActionText(true);
        tbSource.addSeparator();
        tbSource.add(btSourceUncoalesceAll);
        pbSourceProgress = new JProgressBar(0, 100);
        pbSourceProgress.setVisible(false);
        pnSourceBuilder.add(pbSourceProgress, cc.xy(1, 7, CellConstraints.FILL, CellConstraints.FILL));
        // build and set source panel
        spnContexts.setLeftComponent(pnSourceBuilder.build());

        popSource = new JPopupMenu();
        popSource.add(acSourceAddNode);
        popSource.add(acSourceAddChildNode);
        popSource.add(acSourceDelete);

        //build target
        FormLayout pnTargetLayout = new FormLayout("fill:d:grow", "center:d:noGrow,top:4dlu:noGrow,center:d:noGrow,top:4dlu:noGrow,center:d:grow,bottom:2dlu:noGrow,bottom:d:noGrow");
//        PanelBuilder pnTargetBuilder = new PanelBuilder(pnTargetLayout, new FormDebugPanel());
        PanelBuilder pnTargetBuilder = new PanelBuilder(pnTargetLayout);
        JToolBar tbTarget = new JToolBar();
        tbTarget.setFloatable(false);
        pnTargetBuilder.add(tbTarget, cc.xy(1, 1, CellConstraints.FILL, CellConstraints.DEFAULT));
        JButton btTargetCreate = new JButton(acTargetCreate);
        btTargetCreate.setHideActionText(true);
        tbTarget.add(btTargetCreate);
        tbTarget.addSeparator();
        JButton btTargetOpen = new JButton(acTargetOpen);
        btTargetOpen.setHideActionText(true);
        JButton btTargetSave = new JButton(acTargetSave);
        btTargetSave.setHideActionText(true);
        tbTarget.add(btTargetOpen);
        tbTarget.add(btTargetSave);
        teTargetContextLocation = new JTextField();
        teTargetContextLocation.setEnabled(false);
        teTargetContextLocation.setHorizontalAlignment(JTextField.RIGHT);
        pnTargetBuilder.add(teTargetContextLocation, cc.xy(1, 3, CellConstraints.FILL, CellConstraints.FILL));
        ToolTipManager.sharedInstance().registerComponent(teTargetContextLocation);
        spTarget = new JScrollPane();
        pnTargetBuilder.add(spTarget, cc.xy(1, 5, CellConstraints.FILL, CellConstraints.FILL));
        tTarget = new JTree(new DefaultMutableTreeNode(EMPTY_ROOT_NODE_LABEL));
        ToolTipManager.sharedInstance().registerComponent(tTarget);
        tTarget.addMouseListener(treeMouseListener);
        tTarget.addMouseListener(treePopupListener);
        tTarget.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        spTarget.setViewportView(tTarget);
        tbTarget.addSeparator();
        acTargetAddNode = new ActionEditAddNode(tTarget);
        acTargetAddChildNode = new ActionEditAddChildNode(tTarget);
        acTargetDelete = new ActionEditDelete(tTarget);
        JButton btTargetEditAddNode = new JButton(acTargetAddNode);
        JButton btTargetEditAddChildNode = new JButton(acTargetAddChildNode);
        JButton btTargetEditDelete = new JButton(acTargetDelete);
        btTargetEditAddNode.setHideActionText(true);
        btTargetEditAddChildNode.setHideActionText(true);
        btTargetEditDelete.setHideActionText(true);
        tbTarget.add(btTargetEditAddNode);
        tbTarget.add(btTargetEditAddChildNode);
        tbTarget.add(btTargetEditDelete);
        acTargetUncoalesce = new ActionViewUncoalesce(tTarget);
        acTargetUncoalesceAll = new ActionViewUncoalesceAll(tTarget);
        JButton btTargetUncoalesceAll = new JButton(acTargetUncoalesceAll);
        btTargetUncoalesceAll.setHideActionText(true);
        tbTarget.addSeparator();
        tbTarget.add(btTargetUncoalesceAll);
        pbTargetProgress = new JProgressBar(0, 100);
        pbTargetProgress.setVisible(false);
        pnTargetBuilder.add(pbTargetProgress, cc.xy(1, 7, CellConstraints.FILL, CellConstraints.FILL));
        // build and set target panel
        spnContexts.setRightComponent(pnTargetBuilder.build());


        popTarget = new JPopupMenu();
        popTarget.add(acTargetAddNode);
        popTarget.add(acTargetAddChildNode);
        popTarget.add(acTargetDelete);
        log.debug("Built trees");


        //build mapping table
        tblMapping = new JTable(new MappingTableModel(null));
        tblMapping.setFillsViewportHeight(true);
        tblMapping.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblMapping.setDefaultRenderer(IMappingElement.class, mappingTableCellRenderer);
        spnContextsMapping.setBottomComponent(new JScrollPane(tblMapping));

        //build log panel
        JPanel pnLog = new JPanel();
        pnLog.setLayout(new FormLayout("fill:d:grow", "fill:d:grow"));
        spnContextsLog.setBottomComponent(pnLog);
        spLog = new JScrollPane();
        pnLog.add(spLog, cc.xy(1, 1));
        taLog = new JTextArea();
        taLog.setEditable(false);
        spLog.setViewportView(taLog);
        SMatchGUILog4Appender.setTextArea(taLog);
        //to make the JScrollPane wrapping the target component (e.g. JTextArea) automatically scroll down to show the latest log entries
        //TODO init initial scroll to bottom to start tracking
        org.apache.log4j.lf5.viewer.LF5SwingUtils.makeVerticalScrollBarTrack(spLog);

        //build status bar
        pbProgress = new JProgressBar(0, 100);
        builder.add(pbProgress, cc.xy(1, 7, CellConstraints.FILL, CellConstraints.FILL));
        pbProgress.setVisible(false);
        log.debug("Built mapping");

        //FormDebugUtils.dumpAll(builder.getPanel());
        mainPanel = builder.getPanel();
        log.debug("Built main panel");

        buildMenu();
        log.debug("Built menu");

        Action[] actions = new Action[]{
                acSourceCreate, acSourceAddNode, acSourceAddChildNode, acSourceDelete, acSourceUncoalesce, acSourceUncoalesceAll,
                acSourceOpen, acSourcePreprocess, acSourceClose, acSourceSave, acSourceSaveAs,
                acTargetCreate, acTargetAddNode, acTargetAddChildNode, acTargetDelete, acTargetUncoalesce, acTargetUncoalesceAll,
                acTargetOpen, acTargetPreprocess, acTargetClose, acTargetSave, acTargetSaveAs,
                acMappingCreate, acMappingOpen, acMappingClose, acMappingSave, acMappingSaveAs,
                acEditAddNode, acEditAddChildNode, acEditAddLink, acEditDelete,
                acViewUncoalesce, acViewUncoalesceAll, acConfigurationEdit
        };

        for (Action a : actions) {
            if (a instanceof Observer) {
                this.addObserver((Observer) a);
            }
        }
        this.addObserver(this);
        log.debug("Set up actions");
    }

    private JFileChooser getFileChooser() {
        if (null == fc) {
            fc = new JFileChooser();
        }
        return fc;
    }

    /**
     * Creates the tree from a context and a mapping.
     *
     * @param context context
     * @param jTree   a JTree
     * @param mapping a mapping
     */
    private void createTree(final IContext context, final JTree jTree, final IContextMapping<INode> mapping) {
        if (null == context) {
            jTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode(EMPTY_ROOT_NODE_LABEL)));
            jTree.removeTreeSelectionListener(treeSelectionListener);
            jTree.removeFocusListener(treeFocusListener);
            jTree.setCellRenderer(new DefaultTreeCellRenderer());
            jTree.setEditable(false);
        } else {
            TreeModel treeModel;
            clearUserObjects(context.getRoot());
            treeModel = new MappingTreeModel(context.getRoot(), jTree == tSource, mapping);
            jTree.addFocusListener(treeFocusListener);

            jTree.setModel(treeModel);

            //expand all the nodes initially
            if (context.nodesCount() < 60) {
                for (int i = 0; i < jTree.getRowCount(); i++) {
                    jTree.expandRow(i);
                }
            } else {
                //expand first level
                TreePath p = new TreePath(context.getRoot());
                jTree.expandPath(p);
            }

            jTree.setCellEditor(new NodeTreeCellEditor(jTree, mappingTreeCellRenderer));
            jTree.setEditable(true);
            jTree.setCellRenderer(mappingTreeCellRenderer);
        }
        if (jTree == lastFocusedTree) {
            lastFocusedTree = null;
        }
    }

    private void resetMappingInTable() {
        tblMapping.getSelectionModel().removeListSelectionListener(tableSelectionListener);
        tblMapping.setModel(new MappingTableModel(mapping));
        if (null != mapping) {
            tblMapping.getSelectionModel().addListSelectionListener(tableSelectionListener);
        }
    }

    private void clearUserObjects(INode root) {
        if (null != root) {
            root.nodeData().setUserObject(null);
            Iterator<INode> i = root.descendantsIterator();
            while (i.hasNext()) {
                i.next().nodeData().setUserObject(null);
            }
        }
    }

    private void updateMatchManagerConfig(final String newConfig) {
        cbConfig.setEnabled(false);
        log.info("Loading MatchManager with config: " + newConfig);
        SwingWorker<IMatchManager, Void> managerUpdateTask = new SwingWorker<IMatchManager, Void>() {
            private final IMatchManager copy = getMatchManager();

            @Override
            public IMatchManager doInBackground() throws InterruptedException {
                semManager.acquire(PERMITS);
                return MatchManager.getInstanceFromConfigFile(newConfig);
            }

            @Override
            public void done() {
                try {
                    setMatchManager(get());
                    cbConfigPrevIndex = cbConfig.getSelectedIndex();
                } catch (InterruptedException | ExecutionException e) {
                    // rollback the manager
                    setMatchManager(copy);
                    // rollback config index
                    cbConfig.removeItemListener(configComboListener);
                    cbConfig.setSelectedIndex(cbConfigPrevIndex);
                    cbConfig.addItemListener(configComboListener);

                    // show the error message
                    Throwable cause = e;
                    while (null != cause.getCause()) {
                        cause = cause.getCause();
                    }
                    log.error("Error loading configuration", cause);
                    String why = "Error occurred while loading the configuration from " + configFileName + ".\n\n"
                            + cause.getClass().getSimpleName() + ": " + cause.getMessage()
                            + "\n\nPlease, ensure the configuration file is correct and try again.";
                    JOptionPane.showMessageDialog(frame, why, "Configuration load error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    semManager.release(PERMITS);
                    cbConfig.setEnabled(true);
                    setChanged();
                    notifyObservers();
                }
            }
        };
        // switches off all mm dependent methods
        setMatchManager(null);
        setChanged();
        notifyObservers();
        managerUpdateTask.execute();
    }

    private void applyLookAndFeel() {
        if (null != lookAndFeel) {
            try {
                UIManager.setLookAndFeel(lookAndFeel);
            } catch (ClassNotFoundException e) {
                if (log.isEnabledFor(Level.ERROR)) {
                    log.error("ClassNotFoundException", e);
                }
            } catch (InstantiationException e) {
                if (log.isEnabledFor(Level.ERROR)) {
                    log.error("InstantiationException", e);
                }
            } catch (IllegalAccessException e) {
                if (log.isEnabledFor(Level.ERROR)) {
                    log.error("IllegalAccessException", e);
                }
            } catch (UnsupportedLookAndFeelException e) {
                if (log.isEnabledFor(Level.ERROR)) {
                    log.error("UnsupportedLookAndFeelException", e);
                }
            }
        }
    }

    private void showLFIs() {
        System.out.println("Available LookAndFeels:");
        for (UIManager.LookAndFeelInfo lfi : UIManager.getInstalledLookAndFeels()) {
            System.out.println(lfi.getName() + "=" + lfi.getClassName());
        }
    }

    private void readProperties() throws IOException {
        File configFile = new File(GUI_CONF_FILE);
        properties = new Properties();
        if (configFile.exists()) {
            log.info("Reading properties " + GUI_CONF_FILE);
            properties.load(new BufferedReader(new InputStreamReader(new FileInputStream(configFile))));
        }
        parseProperties();
    }

    private void parseProperties() {
        if (properties.containsKey("LookAndFeel")) {
            lookAndFeel = properties.getProperty("LookAndFeel");
        }
    }

    public IMatchManager getMatchManager() {
        return mm;
    }

    public void setMatchManager(IMatchManager mm) {
        this.mm = mm;
    }

    public void startup(String[] args) throws IOException {
        // initialize property file
        configFileName = ".." + File.separator + "conf" + File.separator + "s-match.xml";
        for (String arg : args) {
            if (arg.startsWith(CLI.CONFIG_FILE_CMD_LINE_KEY)) {
                configFileName = arg.substring(CLI.CONFIG_FILE_CMD_LINE_KEY.length());
            }
        }

        showLFIs();
        readProperties();
        applyLookAndFeel();
        buildStaticGUI();
        updateMatchManagerConfig(new File(GUI_CONF_FILE).getParent() + File.separator + cmConfigs.getSelectedItem());

        frame = new JFrame("S-Match GUI");
        frame.setMinimumSize(new Dimension(600, 400));
        frame.setLocation(100, 100);
        frame.setContentPane(mainPanel);
        //to check for matching in progress
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setJMenuBar(mainMenu);
        frame.addWindowListener(windowListener);
        frame.pack();
        frame.setSize(800, 900);

        //try to set an icon
        try {
            nl.ikarus.nxt.priv.imageio.icoreader.lib.ICOReaderSpi.registerIcoReader();
            System.setProperty("nl.ikarus.nxt.priv.imageio.icoreader.autoselect.icon", "true");
            ImageInputStream in = ImageIO.createImageInputStream(SMatchGUI.class.getResourceAsStream(MAIN_ICON_FILE));
            ArrayList<Image> icons = new ArrayList<>();
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                ImageReader r = readers.next();
                r.setInput(in);
                int nr = r.getNumImages(true);
                for (int i = 0; i < nr; i++) {
                    try {
                        icons.add(r.read(i));
                    } catch (Exception e) {
                        log.error("Error occurred while reading icons: " + e.getMessage());
                    }
                }
                frame.setIconImages(icons);
            }
        } catch (Exception e) {
            log.error("Error while loading icon from " + MAIN_ICON_FILE + ": " + e.getMessage());
        }

        setChanged();
        notifyObservers();
        frame.setVisible(true);
        spnContextsLog.setDividerLocation(.9);
        spnContexts.setDividerLocation(.5);
        spnContextsMapping.setDividerLocation(.9);
    }

    private final WindowListener windowListener = new WindowAdapter() {
        public void windowClosing(WindowEvent e) {
            acMappingClose.actionPerformed(null);
            if (!mappingModified) {
                acSourceClose.actionPerformed(null);
                if (!sourceModified) {
                    acTargetClose.actionPerformed(null);
                    if (!targetModified) {
                        e.getWindow().dispose();
                    }
                }
            }
        }
    };

    @Override
    public void execute(Runnable command) {
        SwingUtilities.invokeLater(command);
    }

    public static void main(final String[] args) throws IOException {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    SMatchGUI gui = new SMatchGUI();
                    // route events to Swing
                    AsyncMatchManager.setEventExecutor(gui);
                    gui.startup(args);
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        });
    }
}