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

        // componentChildren[i] = list of component indices whose parent is i
        Map<Integer, List<Integer>> componentChildren = new LinkedHashMap<>();
        for (int i = 1; i < components.size(); i++) {   // skip root (index 0)
            int parent = components.get(i).parent();
            componentChildren.computeIfAbsent(parent, k -> new ArrayList<>()).add(i);
        }

        // nodesByComponent[i] = list of node indices whose parent component is i
        Map<Integer, List<Integer>> nodesByComponent = new LinkedHashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            int ci = nodes.get(i).parent();
            nodesByComponent.computeIfAbsent(ci, k -> new ArrayList<>()).add(i);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("flowchart TD\n\n");

        // Recursively emit nested subgraphs starting from the root
        emitSubgraph(sb, 0, components, componentChildren, nodesByComponent, nodes, "  ");

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

    private static void emitSubgraph(StringBuilder sb, int ci,
                                     List<ComponentInstance> components,
                                     Map<Integer, List<Integer>> children,
                                     Map<Integer, List<Integer>> nodesByComponent,
                                     List<Node> nodes,
                                     String indent) {
        String compName = components.get(ci).name();
        sb.append(indent).append("subgraph comp_").append(ci)
          .append(" [\"").append(escapeMermaidLabel(compName)).append("\"]\n");

        // Emit this component's own nodes
        for (int ni : nodesByComponent.getOrDefault(ci, List.of())) {
            sb.append(indent).append("  ")
              .append(nodeDeclaration(ni, nodes.get(ni)))
              .append("\n");
        }

        // Recurse into child components
        for (int childIdx : children.getOrDefault(ci, List.of())) {
            emitSubgraph(sb, childIdx, components, children, nodesByComponent, nodes, indent + "  ");
        }

        sb.append(indent).append("end\n");
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
