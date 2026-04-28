package com.rhodesgatelang.gategraph;

import com.rhodesgatelang.gateo.v3.ComponentInstance;
import com.rhodesgatelang.gateo.v3.GateObject;
import com.rhodesgatelang.gateo.v3.GateType;
import com.rhodesgatelang.gateo.v3.Node;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MermaidGenerator {

    private MermaidGenerator() {}   // closed immediately — no body needed

    public static String generate(GateObject go) {
        return generate(go, false);
    }

    public static String generate(GateObject go, boolean collapsed) {
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
        sb.append("  classDef literal fill:#3a3a3a,stroke:#9ca3af,color:#f0f0f0;\n");
        sb.append("  classDef component fill:#1a4d4d,stroke:#5eead4,color:#ffffff,stroke-width:2px;\n\n");

        // Recursively emit nested subgraphs starting from the root
        emitSubgraph(sb, 0, components, componentChildren, nodesByComponent, nodes, "  ", collapsed);

        // All edges after subgraphs.
        // When collapsed, internal edges (within one collapsed block) are dropped
        // and cross-boundary edges are remapped to point at the block.
        Set<String> emittedEdges = new LinkedHashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            String dst = representativeId(i, nodes, components, collapsed);
            for (int inputIdx : nodes.get(i).inputs()) {
                String src = representativeId(inputIdx, nodes, components, collapsed);
                if (src.equals(dst)) continue;          // internal — hidden by collapse
                emittedEdges.add(src + " --> " + dst);
            }
        }
        for (String edge : emittedEdges) {
            sb.append("  ").append(edge).append("\n");
        }

        return sb.toString();
    }

    /**
     * The Mermaid id this node should be drawn as. When {@code collapsed} is true,
     * any node living inside a non-root component is replaced by its top-level
     * component block ({@code comp_<idx>}). Otherwise it's just {@code n<idx>}.
     */
    private static String representativeId(int nodeIdx,
                                           List<Node> nodes,
                                           List<ComponentInstance> components,
                                           boolean collapsed) {
        int ci = nodes.get(nodeIdx).parent();
        if (!collapsed || ci == 0) {
            return "n" + nodeIdx;
        }
        // Walk up until the parent is the root (0). That ancestor is the
        // outermost non-root component containing this node.
        while (components.get(ci).parent() != 0) {
            ci = components.get(ci).parent();
        }
        return "comp_" + ci;
    }

    private static void emitSubgraph(StringBuilder sb, int ci,
                                     List<ComponentInstance> components,
                                     Map<Integer, List<Integer>> children,
                                     Map<Integer, List<Integer>> nodesByComponent,
                                     List<Node> nodes,
                                     String indent,
                                     boolean collapsed) {
        String compName = components.get(ci).name();
        sb.append(indent).append("subgraph comp_").append(ci)
          .append(" [\"").append(escapeMermaidLabel(compName)).append("\"]\n");

        // Emit this component's own nodes
        for (int ni : nodesByComponent.getOrDefault(ci, List.of())) {
            sb.append(indent).append("  ")
              .append(nodeDeclaration(ni, nodes.get(ni)))
              .append("\n");
        }

        // Children: in collapsed mode (only when this is the root) emit each
        // child component as a single block node. Otherwise recurse.
        for (int childIdx : children.getOrDefault(ci, List.of())) {
            if (collapsed && ci == 0) {
                String childName = components.get(childIdx).name();
                sb.append(indent).append("  ")
                  .append("comp_").append(childIdx)
                  .append("[\"").append(escapeMermaidLabel(childName)).append("\"]")
                  .append(":::component")
                  .append("\n");
            } else {
                emitSubgraph(sb, childIdx, components, children, nodesByComponent, nodes,
                             indent + "  ", collapsed);
            }
        }

        sb.append(indent).append("end\n");
    }

    private static String nodeDeclaration(int index, Node node) {
        String id = "n" + index;
        // Always wrap in quotes so labels can safely contain '[', ']', '(', ')',
        // '{', '}', '/', etc. — needed for multi-bit names like "a[4]".
        String label = "\"" + escapeMermaidLabel(nodeLabel(node)) + "\"";

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
        } else if (node.type() == GateType.SPLIT) {
            return id + "((" + label + ")):::split";
        } else if (node.type() == GateType.MERGE) {
            return id + "((" + label + ")):::merge";
        } else if (node.type() == GateType.LSL) {
            return id + "((" + label + ")):::lsl";
        } else if (node.type() == GateType.LSR) {
            return id + "((" + label + ")):::lsr";
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
            if (node.value().isPresent()) {
                return Long.toString(node.value().getAsLong());
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
