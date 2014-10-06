package it.unitn.disi.smatch.classifiers;

import it.unitn.disi.smatch.async.AsyncTask;
import it.unitn.disi.smatch.data.trees.IContext;
import it.unitn.disi.smatch.data.trees.INode;
import it.unitn.disi.smatch.data.trees.INodeData;
import orbital.logic.imp.Formula;
import orbital.logic.sign.ParseException;
import orbital.moon.logic.ClassicalLogic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create concept at node formulas for each node of the context.
 * Converts concept at node formula into CNF.
 *
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class CNFContextClassifierOrbital extends CNFContextClassifier implements IAsyncContextClassifier {

    private static final Logger log = LoggerFactory.getLogger(CNFContextClassifierOrbital.class);

    public CNFContextClassifierOrbital() {
        super();
    }

    public CNFContextClassifierOrbital(IContext context) {
        super(context);
    }

    @Override
    public AsyncTask<Void, INode> asyncClassify(IContext context) {
        return new CNFContextClassifierOrbital(context);
    }

    /**
     * Constructs c@node formula for the concept.
     *
     * @param in node to process
     * @throws ContextClassifierException ContextClassifierException
     */
    protected void buildCNode(INode in) throws ContextClassifierException {
        StringBuilder path = new StringBuilder();
        INodeData nd = in.getNodeData();
        String formula = toCNF(in, nd.getcLabFormula());
        if (formula != null && !formula.isEmpty() && !formula.equals(" ")) {
            if (formula.contains(" ")) {
                formula = "(" + formula + ")";
            }
            path.append(formula);
        }
        if (in.hasParent()) {
            formula = in.getParent().getNodeData().getcNodeFormula();
            if (formula != null && !formula.isEmpty() && !formula.equals(" ")) {
                if (2 < path.length()) {
                    path.append(" & ").append(formula);
                } else {
                    path.append(formula);
                }
            }
        }

        nd.setcNodeFormula(path.toString());
    }

    /**
     * Converts the formula into CNF.
     *
     * @param in      the owner of the formula
     * @param formula the formula to convert
     * @return formula in CNF form
     * @throws ContextClassifierException ContextClassifierException
     */
    public static String toCNF(INode in, String formula) throws ContextClassifierException {
        String result = formula;
        if ((formula.contains("&") && formula.contains("|")) || formula.contains("~")) {
            String tmpFormula = formula;
            tmpFormula = tmpFormula.trim();
            try {
                ClassicalLogic cl = new ClassicalLogic();
                if (!tmpFormula.isEmpty()) {
                    tmpFormula = tmpFormula.replace('.', 'P');
                    Formula f = (Formula) (cl.createExpression(tmpFormula));
                    Formula cnf = ClassicalLogic.Utilities.conjunctiveForm(f);
                    tmpFormula = cnf.toString();
                    result = tmpFormula.replace('P', '.');
                } else {
                    result = tmpFormula;
                }
            } catch (ParseException e) {
                final String errMessage = "Logic parse exception: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                if (log.isErrorEnabled()) {
                    log.error("Logic parse exception for: " + formula + " at node: " + in.getNodeData().getName());
                    log.error(errMessage, e);
                }
                throw new ContextClassifierException(errMessage, e);
            }
        }
        return result;
    }
}