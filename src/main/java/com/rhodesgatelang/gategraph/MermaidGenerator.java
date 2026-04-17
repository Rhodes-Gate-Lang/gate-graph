package com.rhodesgatelang.gategraph;

import com.rhodesgatelang.gateo.v2.ComponentInstance;
import com.rhodesgatelang.gateo.v2.GateObject;
import com.rhodesgatelang.gateo.v2.GateType;
import com.rhodesgatelang.gateo.v2.Node;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MermaidGenerator {

    private MermaidGenerator() {}   // closed immediately — no body needed

    public static String generate(GateObject go) {
        List<Node> nodes = go.nodes();
        List<ComponentInstance> components = go.components();

        // Group node indices by their parent component index
        Map<Integer, List<Integer>> byComponent = new LinkedHashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            int compIdx = nodes.get(i).parent();
            byComponent
                .computeIfAbsent(compIdx, k -> new ArrayList<>())
                .add(i);
        }

        StringBuilder sb = new StringBuilder();   // created OUTSIDE the loops
        sb.append("flowchart TD\n\n");            // lowercase 'f' required by Mermaid

        // One subgraph per component
        for (Map.Entry<Integer, List<Integer>> entry : byComponent.entrySet()) {
            int ci = entry.getKey();
            String compName = (ci < components.size())
                ? components.get(ci).name()       // fixed typo: components, not comonents
                : "component_" + ci;

            sb.append("  subgraph comp_").append(ci)
              .append(" [\"").append(escapeMermaidLabel(compName)).append("\"]\n");

            for (int ni : entry.getValue()) {
                sb.append("    ")
                  .append(nodeDeclaration(ni, nodes.get(ni)))
                  .append("\n");
            }

            sb.append("  end\n\n");
        }

        // All edges AFTER subgraphs
        for (int i = 0; i < nodes.size(); i++) {
            for (int inputIdx : nodes.get(i).inputs()) {
                sb.append("  n").append(inputIdx)
                  .append(" --> n").append(i)
                  .append("\n");
            }
        }

        return sb.toString();
    }

    private static String nodeDeclaration(int index, Node node) {
        String id = "n" + index;
        String label = escapeMermaidLabel(nodeLabel(node));

        // GateType is the correct enum name (not NodeType)
        if (node.type() == GateType.INPUT || node.type() == GateType.OUTPUT) {
            return id + "([" + label + "])";   // pill shape
        } else if (node.type() == GateType.AND) {
            return id + "[" + label + "]";     // rectangle
        } else if (node.type() == GateType.OR) {
            return id + "(" + label + ")";     // rounded rect
        } else if (node.type() == GateType.XOR) {
            return id + "{{" + label + "}}";   // hexagon
        } else if (node.type() == GateType.NOT) {
            return id + "[/" + label + "/]";   // parallelogram
        } else if (node.type() == GateType.LITERAL) {
            return id + "((" + label + "))";   // circle
        } else {
            return id + "[" + label + "]";     // rectangle fallback
        }
    }

    private static String nodeLabel(Node node) {
        if (node.type() == GateType.INPUT || node.type() == GateType.OUTPUT) {
            String base = node.name().orElse(node.type().name().toLowerCase());
            if (node.width() > 1) {
                return base + "[" + node.width() + "]";
            } else {
                return base;
            }
        } else if (node.type() == GateType.LITERAL) {
            if (node.literalValue().isPresent()) {
                return Long.toString(node.literalValue().getAsLong());
            } else {
                return "lit";
            }
        } else {
            return node.type().name().toLowerCase();
        }
    }

    private static String escapeMermaidLabel(String s) {
        return s.replace("\"", "#quot;");
    }
}
