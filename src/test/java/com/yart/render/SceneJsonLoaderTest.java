package com.yart.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneJsonLoaderTest {

    @Test
    void loadSceneBuildsGeometryAndCameraPreset() throws IOException {
        String sceneJson = """
                {
                  "geometry": [
                    {
                      "type": "rectangle",
                      "p1": [0,0,0],
                      "p2": [1,0,0],
                      "p3": [1,1,0],
                      "p4": [0,1,0],
                      "ac": [0.4, 0.5, 2.0],
                      "dc": [-1.0, 0.7, 0.8]
                    },
                    {
                      "type": "triangle",
                      "p1": [0,0,0],
                      "p2": [0,0,1],
                      "p3": [0,1,0]
                    },
                    {
                      "type": "sphere",
                      "centre": [0,0,0],
                      "radius": 0.5
                    },
                    {
                      "type": "rectangle",
                      "visible": false,
                      "p1": [0,0,0],
                      "p2": [1,0,0],
                      "p3": [1,1,0],
                      "p4": [0,1,0]
                    }
                  ],
                  "output": {
                    "centre": [0,0,5],
                    "lookat": [0,0,0],
                    "fov": 10
                  }
                }
                """;

        Path scene = Files.createTempFile("scene", ".json");
        Files.writeString(scene, sceneJson);

        LoadedScene loaded = SceneJsonLoader.loadScene(scene.toFile());

        assertEquals(531, loaded.model().triangles().size());
        Triangle first = loaded.model().triangles().getFirst();
        assertEquals(new Vec3(0.4, 0.5, 1.0), first.ambientColor());
        assertEquals(new Vec3(0.0, 0.7, 0.8), first.diffuseColor());

        assertNotNull(loaded.cameraPreset());
        assertEquals(Math.toRadians(20.0), loaded.cameraPreset().fovRadians(), 1e-9);
        assertTrue(loaded.cameraPreset().zoom() > 0.0);
    }

    @Test
    void loadReturnsModelOnly() throws IOException {
        String sceneJson = """
                {
                  "geometry": [
                    {
                      "type": "triangle",
                      "p1": [0,0,0],
                      "p2": [1,0,0],
                      "p3": [0,1,0]
                    }
                  ]
                }
                """;

        Path scene = Files.createTempFile("scene-model", ".json");
        Files.writeString(scene, sceneJson);

        ObjModel model = SceneJsonLoader.load(scene.toFile());

        assertEquals(1, model.triangles().size());
    }

    @Test
    void cameraPresetCanBeParsedFromOutputArrayWithCenterAlias() throws IOException {
        String sceneJson = """
                {
                  "geometry": [
                    {
                      "type": "triangle",
                      "p1": [0,0,0],
                      "p2": [1,0,0],
                      "p3": [0,1,0]
                    }
                  ],
                  "output": [
                    {
                      "center": [0,0,3]
                    }
                  ]
                }
                """;

        Path scene = Files.createTempFile("scene-output-array", ".json");
        Files.writeString(scene, sceneJson);

        LoadedScene loaded = SceneJsonLoader.loadScene(scene.toFile());
        assertNotNull(loaded.cameraPreset());
    }

    @Test
    void loadSceneHandlesMissingOrInvalidOutputAndDegenerateCamera() throws IOException {
        String noOutput = """
                {
                  "geometry": [
                    {
                      "type": "triangle",
                      "p1": [0,0,0],
                      "p2": [1,0,0],
                      "p3": [0,1,0]
                    }
                  ]
                }
                """;
        String zeroZoomOutput = """
                {
                  "geometry": [
                    {
                      "type": "triangle",
                      "p1": [0,0,0],
                      "p2": [0,0,0],
                      "p3": [0,0,0]
                    }
                  ],
                  "output": {
                    "centre": [0,0,0]
                  }
                }
                """;

        Path noOutputPath = Files.createTempFile("scene-no-output", ".json");
        Path zeroZoomPath = Files.createTempFile("scene-zero-zoom", ".json");
        Files.writeString(noOutputPath, noOutput);
        Files.writeString(zeroZoomPath, zeroZoomOutput);

        assertNull(SceneJsonLoader.loadScene(noOutputPath.toFile()).cameraPreset());
        assertNull(SceneJsonLoader.loadScene(zeroZoomPath.toFile()).cameraPreset());
    }

    @Test
    void loadSceneThrowsForInvalidSchema() throws IOException {
        Path badRoot = Files.createTempFile("scene-bad-root", ".json");
        Path missingGeometry = Files.createTempFile("scene-missing-geometry", ".json");

        Files.writeString(badRoot, "[]");
        Files.writeString(missingGeometry, "{\"output\":{}}");

        assertThrows(IllegalArgumentException.class, () -> SceneJsonLoader.loadScene(badRoot.toFile()));
        assertThrows(IllegalArgumentException.class, () -> SceneJsonLoader.loadScene(missingGeometry.toFile()));
    }
}
