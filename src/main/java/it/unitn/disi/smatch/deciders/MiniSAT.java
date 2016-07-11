package it.unitn.disi.smatch.deciders;

import org.opensat.ContradictionException;
import org.opensat.Dimacs;
import org.opensat.ISolver;
import org.opensat.ParseFormatException;
import org.opensat.minisat.SolverFactory;

import java.io.BufferedReader;
import java.io.LineNumberReader;
import java.io.StringReader;

public class MiniSAT implements ISATSolver {

    public boolean isSatisfiable(String input) throws SATSolverException {
        ISolver solver = SolverFactory.newMiniLearning();
        try {
            LineNumberReader lnrCNF = new LineNumberReader(new BufferedReader(new StringReader(input)));
            Dimacs parser = new Dimacs();
            parser.parseInstance(lnrCNF, solver);
            return solver.solve();
        } catch (ParseFormatException e) {
            throw new SATSolverException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        } catch (ContradictionException e) {
            return false;
        } catch (Exception e) {
            throw new SATSolverException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }
}