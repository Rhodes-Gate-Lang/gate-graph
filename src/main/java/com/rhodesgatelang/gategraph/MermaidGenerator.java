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

        // Color code gates by type
        sb.append("  classDef input   fill:#2d5a3d,stroke:#4ade80,color:#e6ffe6;\n");
        sb.append("  classDef output  fill:#5a3d2d,stroke:#fb923c,color:#fff1e6;\n");
        sb.append("  classDef andGate fill:#1e3a5f,stroke:#60a5fa,color:#e6f0ff;\n");
        sb.append("  classDef orGate  fill:#3d2d5a,stroke:#a78bfa,color:#f0e6ff;\n");
        sb.append("  classDef xorGate fill:#2d5a5a,stroke:#22d3ee,color:#e6ffff;\n");
        sb.append("  classDef notGate fill:#5a5a2d,stroke:#eab308,color:#ffffe6;\n");
        sb.append("  classDef literal fill:#3a3a3a,stroke:#9ca3af,color:#f0f0f0;\n\n");

        // Recursively emit nested subgraphs starting from the root
        emitSubgraph(sb, 0, components, componentChildren, nodesByComponent, nodes, "  ");

        // All edges after subgraphs
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

        // Shape and color tag per gate type
        if (node.type() == GateType.INPUT) {
            return id + "([" + label + "]):::input";
        } else if (node.type() == GateType.OUTPUT) {
            return id + "([" + label + "]):::output";
        } else if (node.type() == GateType.AND) {
            return id + "[" + label + "]:::andGate";
        } else if (node.type() == GateType.OR) {
            return id + "(" + label + "):::orGate";
        } else if (node.type() == GateType.XOR) {
            return id + "{{" + label + "}}:::xorGate";
        } else if (node.type() == GateType.NOT) {
            return id + "[/" + label + "/]:::notGate";
        } else if (node.type() == GateType.LITERAL) {
            return id + "((" + label + ")):::literal";
        } else {
            return id + "[" + label + "]";
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
