package com.yart.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjLoaderTest {

    @Test
    void loadTriangulatesFacesAndAppliesMaterialColors() throws IOException {
        Path dir = Files.createTempDirectory("objloader-test-");
        Path obj = dir.resolve("shape.obj");
        Path mtl = dir.resolve("shape.mtl");

        Files.writeString(mtl, String.join("\n",
                "newmtl mat1",
                "Ka 0.2 0.3 2.0",
                "Kd -1.0 0.6 0.7"));

        Files.writeString(obj, String.join("\n",
                "mtllib shape.mtl",
                "v 0 0 0",
                "v 1 0 0",
                "v 1 1 0",
                "v 0 1 0",
                "usemtl mat1",
                "f 1 2 3 4"));

        ObjModel model = ObjLoader.load(obj.toFile());
        List<Triangle> triangles = model.triangles();

        assertEquals(2, triangles.size());

        Triangle first = triangles.getFirst();
        assertEquals(new Vec3(-0.5, -0.5, 0.0), first.a());
        assertEquals(new Vec3(0.5, -0.5, 0.0), first.b());
        assertEquals(new Vec3(0.5, 0.5, 0.0), first.c());

        assertEquals(new Vec3(0.2, 0.3, 1.0), first.ambientColor());
        assertEquals(new Vec3(0.0, 0.6, 0.7), first.diffuseColor());
    }

    @Test
    void loadSupportsNegativeIndicesAndMissingMaterials() throws IOException {
        Path dir = Files.createTempDirectory("objloader-negative-");
        Path obj = dir.resolve("negative.obj");

        Files.writeString(obj, String.join("\n",
                "# comment",
                "v 0 0 0",
                "v 0 1 0",
                "v 0 0 1",
                "usemtl unknown",
                "f -3 -2 -1"));

        ObjModel model = ObjLoader.load(obj.toFile());
        Triangle tri = model.triangles().getFirst();

        // Model should still load and normalize even when no valid MTL entry is found.
        assertEquals(1, model.triangles().size());
        assertEquals(new Vec3(0.10, 0.10, 0.10), tri.ambientColor());
        assertEquals(1.0, tri.diffuseColor().length(), 1.0);
    }
}
