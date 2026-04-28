package com.rhodesgatelang.gategraph;

import com.rhodesgatelang.gateo.Gateo;
import com.rhodesgatelang.gateo.v3.ComponentInstance;
import com.rhodesgatelang.gateo.v3.GateObject;
import com.rhodesgatelang.gateo.v3.Node;
import java.nio.file.Path;

/**
 * Minimal check that {@code gateo-java} can deserialize a {@code .gateo} file into {@link GateObject}.
 */
public final class GateoSmoke {

  public static void main(String[] args) throws Exception {
    Path path =
        args.length > 0
            ? Path.of(args[0])
            : Path.of(".", "FullAdder.gateo").normalize().toAbsolutePath();

    GateObject go = Gateo.read(path);

    System.out.println("Read: " + path);
    System.out.println(
        "Schema version: " + go.version().major() + "." + go.version().minor());
    System.out.println("Component instances: " + go.components().size());
    for (int i = 0; i < go.components().size(); i++) {
      ComponentInstance c = go.components().get(i);
      System.out.println("  [" + i + "] name=" + c.name() + " parent=" + c.parent());
    }
    System.out.println("Nodes: " + go.nodes().size());
    int limit = Math.min(8, go.nodes().size());
    for (int i = 0; i < limit; i++) {
      Node n = go.nodes().get(i);
      System.out.println(
          "  ["
              + i
              + "] type="
              + n.type()
              + " width="
              + n.width()
              + " parent="
              + n.parent()
              + " inputs="
              + n.inputs()
              + " name="
              + n.name()
              + " literal="
              + n.value());
    }
    if (go.nodes().size() > limit) {
      System.out.println("  ... (" + (go.nodes().size() - limit) + " more nodes)");
    }
  }

  private GateoSmoke() {}
}
