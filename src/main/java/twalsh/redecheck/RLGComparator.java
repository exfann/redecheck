package twalsh.redecheck;
import com.rits.cloning.Cloner;
import org.apache.commons.lang3.StringUtils;
import twalsh.reporting.*;
import twalsh.reporting.Error;
import twalsh.rlg.*;
import edu.gatech.xpert.dom.*;

import java.awt.Desktop;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import java.io.PrintWriter;
import org.apache.commons.io.FileUtils;
import java.io.File;

/**
 * Created by thomaswalsh on 20/08/15.
 */
public class RLGComparator {
    // Instance variables
    ResponsiveLayoutGraph rlg1,rlg2;
    public HashMap<Node, Node> matchedNodes;
    Cloner cloner;
    public ArrayList<String> issues;
    public static ArrayList<Error> errors;
    public static ArrayList<VisibilityError> vcErrors;
    public static ArrayList<AlignmentError> acErrors;
    public static ArrayList<WidthError> wcErrors;
    int[] defaultWidths;

    /**
     * Constructor for the RLGComparator object
     * @param r1    the oracle RLG
     * @param r2    the test RLG
     */
    public RLGComparator(ResponsiveLayoutGraph r1, ResponsiveLayoutGraph r2) {
        rlg1 = r1;
        rlg2 = r2;
        vcErrors = new ArrayList<>();
        wcErrors = new ArrayList<>();
        acErrors = new ArrayList<>();
        errors = new ArrayList<Error>();
        defaultWidths = new int[] {400, 640, 768, 1024};
    }

    /**
     * Executes the overall comparison process
     */
    public void compare() {
        matchedNodes = new HashMap<Node, Node>();
        cloner = new Cloner();
        matchNodes();
    }

    /**
     * Takes a set of matched nodes from the two RLG models and compares all the different constraints on them, producing
     * a list of model differences.
     * @return      the list of model differences between the two versions of the page
     */
    public ArrayList<String> compareMatchedNodes() {
        int counter = 1;
        for (Node n : matchedNodes.keySet()) {
            Node m = matchedNodes.get(n);
            compareVisibilityConstraints(n, m);
            compareAlignmentConstraints(n, m);
            compareWidthConstraints(n, m);
            double progressPerc = ((double) counter/ (double)matchedNodes.size())* 100;
            System.out.print("\rPROGRESS : | " + StringUtils.repeat("=", (int) progressPerc) + StringUtils.repeat(" ", 100 - (int)progressPerc) + " | " + (int)progressPerc + "%");
            counter++;
        }
        return issues;
    }

    /**
     * Matches the nodes from the oracle version to those in the test version, so their constraints can then be compared.
     */
    public void matchNodes() {
        HashMap<String, Node> nodes1 = cloner.deepClone(rlg1.getNodes());
        HashMap<String, Node> nodes2 = cloner.deepClone(rlg2.getNodes());

        // Match the nodes and their min/max values
        for (Node n1 : rlg1.getNodes().values()) {
            String xpath1 = n1.getXpath();
            for (Node n2 : rlg2.getNodes().values()) {
                String xpath2 = n2.getXpath();
                if (xpath1.equals(xpath2)) {
                    matchedNodes.put(n1, n2);
                    nodes1.remove(xpath1);
                    nodes2.remove(xpath2);
                }
            }
        }
        for (Node left1 : nodes1.values()) {
            System.out.println(left1.getXpath() + " wasn't matched in Graph 2");
        }
        for (Node left2 : nodes2.values()) {
            System.out.println(left2.getXpath() + " wasn't matched in Graph 1");
        }
    }

    /**
     * Compares the visibility constraints of a pair of matched nodes
     * @param n     the first node being compared
     * @param m     the second node being compared
     */
    public void compareVisibilityConstraints(Node n, Node m) {
        VisibilityConstraint a = n.getVisibilityConstraints().get(0);
        VisibilityConstraint b = m.getVisibilityConstraints().get(0);
        if ((a.appear != b.appear) || (a.disappear != b.disappear)) {
            VisibilityError ve = new VisibilityError(n, m);
            vcErrors.add(ve);
        }
    }

    /**
     * Compares the alignment constraints of a pair of matched nodes
     * @param n     the first node being compared
     * @param m     the second node being compared
     */
    public void compareAlignmentConstraints(Node n, Node m) {
        ArrayList<AlignmentConstraint> ac1 = new ArrayList<AlignmentConstraint>(), ac2 = new ArrayList<AlignmentConstraint>();

        // Get all the alignment constraints for the matched nodes from the two graphs
        for (AlignmentConstraint a : rlg1.getAlignments().values()) {
            if (a.node1.getXpath().equals(n.getXpath())) {
                ac1.add(a);
            }
        }

        for (AlignmentConstraint b : rlg2.getAlignments().values()) {
            if (b.node1.getXpath().equals(m.getXpath())) {
                ac2.add(b);
            }
        }

        HashMap<AlignmentConstraint, AlignmentConstraint> matched = new HashMap<AlignmentConstraint, AlignmentConstraint>();
        ArrayList<AlignmentConstraint> unmatched1 = new ArrayList<AlignmentConstraint>(), unmatched2 = new ArrayList<AlignmentConstraint>();
        while (ac1.size() > 0) {
            AlignmentConstraint ac = ac1.remove(0);
            AlignmentConstraint match = null;
            for (AlignmentConstraint temp : ac2) {
                if ( (temp.node1.getXpath().equals(ac.node1.getXpath())) && (temp.node2.getXpath().equals(ac.node2.getXpath())) ) {
                    if ((temp.getMin() == ac.getMin()) && (temp.getMax() == ac.getMax()) && (Arrays.equals(ac.getAttributes(), temp.getAttributes()))) {
                        match = temp;
                    } else if ((temp.getMin()== ac.getMin()) && (temp.getMax() == ac.getMax()) && (!Arrays.equals(ac.getAttributes(), temp.getAttributes()))){
                        AlignmentError ae = new AlignmentError(ac, temp, "diffAttributes");
                        acErrors.add(ae);
                        match = temp;
                    } else if ( (Arrays.equals(ac.getAttributes(), temp.getAttributes())) && ((temp.getMin() != ac.getMin()) || (temp.getMax() != ac.getMax())) ) {
                        AlignmentError ae = new AlignmentError(ac, temp, "diffBounds");
                        acErrors.add(ae);
                        match = temp;
                    }
                }
            }
            if (match != null) {
                matched.put(ac, match);
                ac2.remove(match);
            } else {
                AlignmentError ae = new AlignmentError(ac, null, "unmatched-oracle");
                acErrors.add(ae);
            }
        }
        for (AlignmentConstraint acUM : ac2) {
            AlignmentError ae = new AlignmentError(null, acUM, "unmatched-test");
            acErrors.add(ae);
        }
    }

    /**
     * Compares the width constraints for a pair of matched nodes
     * @param n     the first node being compared
     * @param m     the second node being compared
     */
    public void compareWidthConstraints(Node n, Node m) {
        ArrayList<WidthConstraint> wc1 = new ArrayList<WidthConstraint>(), wc2 = new ArrayList<WidthConstraint>();

        for (WidthConstraint w : n.getWidthConstraints()) {
            wc1.add(w);
        }
        for (WidthConstraint w : m.getWidthConstraints()) {
            wc2.add(w);
        }

        HashMap<WidthConstraint, WidthConstraint> matchedConstraints = new HashMap<WidthConstraint, WidthConstraint>();
        ArrayList<WidthConstraint> unmatch1 = new ArrayList<WidthConstraint>();
        ArrayList<WidthConstraint> unmatch2 = new ArrayList<WidthConstraint>();

        while (wc1.size() > 0) {
            WidthConstraint wc = wc1.remove(0);
            WidthConstraint match = null;
            for (WidthConstraint temp : wc2) {
                if ( (wc.getPercentage() == temp.getPercentage()) && (wc.getAdjustment() == temp.getAdjustment()) && (wc.getMin() == temp.getMin()) && (wc.getMax() == temp.getMax())) {
                    match = temp;
                } else if ( ((wc.getMin()==temp.getMin()) && (wc.getMax()==temp.getMax())) && ( (wc.getPercentage()!=temp.getPercentage()) || (wc.getAdjustment()!=temp.getAdjustment()) ) ) {
                    WidthError we = new WidthError(wc, temp, "diffCoefficients", n.getXpath());
                    wcErrors.add(we);
                    match = temp;
                } else if ( ((wc.getMin()!=temp.getMin()) || (wc.getMax()!=temp.getMax())) && ( (wc.getPercentage()==temp.getPercentage()) && (wc.getAdjustment()==temp.getAdjustment()) ) ) {
                    WidthError we = new WidthError(wc, temp, "diffBounds",n.getXpath());
                    wcErrors.add(we);
                    match = temp;
                }
            }

            // Update the sets
            if (match != null) {
                matchedConstraints.put(wc, match);
                wc2.remove(match);
            } else {
                WidthError we = new WidthError(wc, null, "unmatched-oracle",n.getXpath());
                wcErrors.add(we);
            }
        }
        for (WidthConstraint c : wc2) {
            WidthError we = new WidthError(null, c, "unmatched-test",n.getXpath());
            wcErrors.add(we);
        }

    }

    /**
     * Writes the model differences between the oracle and test versions into a text file, and then opens the file in
     * the default program of the user's system.
     * @param folder        the folder name in which the file is saved
     * @param fileName      the name of the results file
     */
    public void writeRLGDiffToFile(String folder, String fileName) {
        PrintWriter output = null;
        String outFolder = "";
        try {
            outFolder = folder + "/reports/";
            FileUtils.forceMkdir(new File(outFolder));
            output = new PrintWriter(outFolder + fileName + ".txt");

            // Print out visibility errors
            output.append("====== Visibility Errors ====== \n\n");
            for (VisibilityError e : vcErrors) {
                if (isErrorUnseen(e, defaultWidths)) {
                    output.append(e.toString());
                }
            }

            // Print out alignment errors
            output.append("====== Alignment Errors ====== \n\n");
            Collections.sort(acErrors);
            String previousKey = "";
            for (AlignmentError e : acErrors) {
                if(isErrorUnseen(e, defaultWidths)) {
                    // Check if this is a different edge
                    if (!e.generateKey().equals(previousKey)) {
                        if (e.getOracle() != null) {
                            output.append("\n" + e.getOracle().node1.getXpath() + " -> " + e.getOracle().node2.getXpath() + "\n");
                        } else {
                            output.append("\n" + e.getTest().node1.getXpath() + " -> " + e.getTest().node2.getXpath() + "\n");
                        }
                        previousKey = e.generateKey();
                    }
                    output.append("\n" + e.toString());
                }
            }

            // Print out width errors
            output.append("====== Width Errors ====== \n\n");
            Collections.sort(wcErrors);
            String previousXP = "";
            for (WidthError e : wcErrors) {
                if (!e.getXPath().equals(previousXP)) {

                    if (isErrorUnseen(e, defaultWidths)) {
                        if (!e.getXPath().equals(previousXP)) {
                            output.append(e.getXPath() + "\n");
                        }
                        output.append(e.toString());
                    }

                    previousXP = e.getXPath();
                }
            }

            output.close();
            Desktop d = Desktop.getDesktop();
            d.open(new File(outFolder + fileName + ".txt"));
        } catch (IOException e) {
            System.out.println("Failed to write the results to file.");
        }
    }

    public boolean isErrorUnseen(Error e, int[] widths) {
        ArrayList<int[]> errorRanges;
        if (e instanceof VisibilityError)
            errorRanges = ((VisibilityError) e).calculateRangeOfViewportWidths();
        else if (e instanceof AlignmentError) {
            errorRanges = ((AlignmentError) e).calculateRangeOfViewportWidths();
        } else {
            errorRanges = ((WidthError) e).calculateRangeOfViewportWidths();
        }

        for (int i = 0; i < widths.length; i++) {
            int w = widths[i];
            for (int[] range : errorRanges) {
                if ((range[0] <= w) && (range[1] >= w)) {
                    return false;
                }
            }
        }
        return true;
    }
}
