package com.example.step_1_render_skeleton;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class SkeletonActivity extends Activity {

    private GLSurfaceView glView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        glView = new SkeletonGLView(this);

        setContentView(glView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        glView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        glView.onResume();
    }

    // ------------------------------------------------
    // OPENGL VIEW
    // ------------------------------------------------

    private static class SkeletonGLView extends GLSurfaceView {

        private final SkeletonRenderer renderer;

        private float previousX;
        private float previousY;

        public SkeletonGLView(Activity context) {
            super(context);

            setEGLContextClientVersion(2);

            renderer = new SkeletonRenderer();

            setRenderer(renderer);

            setRenderMode(
                    GLSurfaceView.RENDERMODE_CONTINUOUSLY
            );
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {

            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:

                    previousX = x;
                    previousY = y;
                    return true;

                case MotionEvent.ACTION_MOVE:

                    float dx = x - previousX;
                    float dy = y - previousY;

                    renderer.rotate(dx, dy);

                    previousX = x;
                    previousY = y;

                    return true;
            }

            return true;
        }
    }

    // ------------------------------------------------
    // SKELETON RENDERER
    // ------------------------------------------------

    private static class SkeletonRenderer
            implements GLSurfaceView.Renderer {

        private int program;

        private int positionHandle;
        private int colorHandle;
        private int mvpHandle;
        private int pointSizeHandle;

        private FloatBuffer jointBuffer;
        private FloatBuffer boneBuffer;

        private final float[] projectionMatrix =
                new float[16];

        private final float[] viewMatrix =
                new float[16];

        private final float[] modelMatrix =
                new float[16];

        private final float[] mvpMatrix =
                new float[16];

        private float rotationX = 0;
        private float rotationY = 0;

        // --------------------------------------------
        // JOINT POSITIONS
        // Each row contains X, Y, Z.
        // --------------------------------------------

        private final float[][] joints = {

                { 0.0f,  2.0f, 0.0f }, // 0 Head
                { 0.0f,  1.6f, 0.0f }, // 1 Neck
                { 0.0f,  0.9f, 0.0f }, // 2 Chest
                { 0.0f,  0.0f, 0.0f }, // 3 Pelvis

                {-0.7f,  1.1f, 0.0f }, // 4 Left shoulder
                {-1.2f,  0.3f, 0.0f }, // 5 Left hand

                { 0.7f,  1.1f, 0.0f }, // 6 Right shoulder
                { 1.2f,  0.3f, 0.0f }, // 7 Right hand

                {-0.4f, -1.0f, 0.0f }, // 8 Left foot
                { 0.4f, -1.0f, 0.0f }  // 9 Right foot
        };

        // --------------------------------------------
        // BONE CONNECTIONS
        // Each pair references two joint IDs.
        // --------------------------------------------

        private final int[][] bones = {

                {0, 1},
                {1, 2},
                {2, 3},

                {2, 4},
                {4, 5},

                {2, 6},
                {6, 7},

                {3, 8},
                {3, 9}
        };

        // --------------------------------------------
        // SHADERS
        // --------------------------------------------

        private final String vertexShader =
                "uniform mat4 uMVPMatrix;" +
                        "uniform float uPointSize;" +
                        "attribute vec4 aPosition;" +
                        "void main() {" +
                        " gl_Position = uMVPMatrix * aPosition;" +
                        " gl_PointSize = uPointSize;" +
                        "}";

        private final String fragmentShader =
                "precision mediump float;" +
                        "uniform vec4 uColor;" +
                        "void main() {" +
                        " gl_FragColor = uColor;" +
                        "}";

        // --------------------------------------------
        // INITIALIZATION
        // --------------------------------------------

        @Override
        public void onSurfaceCreated(
                GL10 gl, EGLConfig config) {

            GLES20.glClearColor(
                    0.06f, 0.07f, 0.10f, 1.0f
            );

            GLES20.glEnable(
                    GLES20.GL_DEPTH_TEST
            );

            program = createProgram(
                    vertexShader,
                    fragmentShader
            );

            positionHandle =
                    GLES20.glGetAttribLocation(
                            program, "aPosition"
                    );

            colorHandle =
                    GLES20.glGetUniformLocation(
                            program, "uColor"
                    );

            mvpHandle =
                    GLES20.glGetUniformLocation(
                            program, "uMVPMatrix"
                    );

            pointSizeHandle =
                    GLES20.glGetUniformLocation(
                            program, "uPointSize"
                    );

            createBuffers();

            Matrix.setLookAtM(
                    viewMatrix,
                    0,
                    0, 0, 5,
                    0, 0, 0,
                    0, 1, 0
            );
        }

        // --------------------------------------------
        // CREATE VERTEX BUFFERS
        // --------------------------------------------

        private void createBuffers() {

            float[] boneVertices =
                    new float[bones.length * 2 * 3];

            int index = 0;

            for (int[] bone : bones) {

                for (int jointID : bone) {

                    for (int axis = 0; axis < 3; axis++) {

                        boneVertices[index++] =
                                joints[jointID][axis];
                    }
                }
            }

            boneBuffer = createFloatBuffer(
                    boneVertices
            );

            float[] jointVertices =
                    new float[joints.length * 3];

            index = 0;

            for (float[] joint : joints) {

                for (float coordinate : joint) {

                    jointVertices[index++] =
                            coordinate;
                }
            }

            jointBuffer = createFloatBuffer(
                    jointVertices
            );
        }

        private FloatBuffer createFloatBuffer(
                float[] values) {

            ByteBuffer bb = ByteBuffer.allocateDirect(
                    values.length * 4
            );

            bb.order(ByteOrder.nativeOrder());

            FloatBuffer buffer = bb.asFloatBuffer();

            buffer.put(values);

            buffer.position(0);

            return buffer;
        }

        // --------------------------------------------
        // SCREEN SIZE / CAMERA PROJECTION
        // --------------------------------------------

        @Override
        public void onSurfaceChanged(
                GL10 gl, int width, int height) {

            GLES20.glViewport(
                    0, 0, width, height
            );

            float ratio =
                    (float) width / (float) height;

            Matrix.frustumM(
                    projectionMatrix,
                    0,
                    -ratio,
                    ratio,
                    -1,
                    1,
                    2,
                    20
            );
        }

        // --------------------------------------------
        // DRAW FRAME
        // --------------------------------------------

        @Override
        public void onDrawFrame(GL10 gl) {

            GLES20.glClear(
                    GLES20.GL_COLOR_BUFFER_BIT |
                            GLES20.GL_DEPTH_BUFFER_BIT
            );

            GLES20.glUseProgram(program);

            // Model rotation

            Matrix.setIdentityM(
                    modelMatrix, 0
            );

            Matrix.rotateM(
                    modelMatrix,
                    0,
                    rotationX,
                    1, 0, 0
            );

            Matrix.rotateM(
                    modelMatrix,
                    0,
                    rotationY,
                    0, 1, 0
            );

            // Combine camera and model matrices

            float[] temp = new float[16];

            Matrix.multiplyMM(
                    temp, 0,
                    viewMatrix, 0,
                    modelMatrix, 0
            );

            Matrix.multiplyMM(
                    mvpMatrix, 0,
                    projectionMatrix, 0,
                    temp, 0
            );

            GLES20.glUniformMatrix4fv(
                    mvpHandle,
                    1,
                    false,
                    mvpMatrix,
                    0
            );

            // Draw bones

            GLES20.glUniform4f(
                    colorHandle,
                    0.85f, 0.85f, 0.90f, 1.0f
            );

            GLES20.glUniform1f(
                    pointSizeHandle, 1.0f
            );

            GLES20.glLineWidth(2.0f);

            GLES20.glEnableVertexAttribArray(
                    positionHandle
            );

            GLES20.glVertexAttribPointer(
                    positionHandle,
                    3,
                    GLES20.GL_FLOAT,
                    false,
                    0,
                    boneBuffer
            );

            GLES20.glDrawArrays(
                    GLES20.GL_LINES,
                    0,
                    bones.length * 2
            );

            // Draw joints

            GLES20.glUniform4f(
                    colorHandle,
                    0.1f, 0.7f, 1.0f, 1.0f
            );

            GLES20.glUniform1f(
                    pointSizeHandle, 18.0f
            );

            GLES20.glVertexAttribPointer(
                    positionHandle,
                    3,
                    GLES20.GL_FLOAT,
                    false,
                    0,
                    jointBuffer
            );

            GLES20.glDrawArrays(
                    GLES20.GL_POINTS,
                    0,
                    joints.length
            );

            GLES20.glDisableVertexAttribArray(
                    positionHandle
            );
        }

        // --------------------------------------------
        // CAMERA ROTATION
        // --------------------------------------------

        public void rotate(float dx, float dy) {

            rotationY += dx * 0.5f;
            rotationX += dy * 0.5f;
        }

        // --------------------------------------------
        // SHADER PROGRAM CREATION
        // --------------------------------------------

        private int createProgram(
                String vertexSource,
                String fragmentSource) {

            int vertex = loadShader(
                    GLES20.GL_VERTEX_SHADER,
                    vertexSource
            );

            int fragment = loadShader(
                    GLES20.GL_FRAGMENT_SHADER,
                    fragmentSource
            );

            int program = GLES20.glCreateProgram();

            GLES20.glAttachShader(program, vertex);

            GLES20.glAttachShader(program, fragment);

            GLES20.glLinkProgram(program);

            int[] status = new int[1];

            GLES20.glGetProgramiv(
                    program,
                    GLES20.GL_LINK_STATUS,
                    status,
                    0
            );

            if (status[0] == 0) {

                String error =
                        GLES20.glGetProgramInfoLog(program);

                GLES20.glDeleteProgram(program);

                throw new RuntimeException(
                        "Program linking failed: " + error
                );
            }

            GLES20.glDeleteShader(vertex);
            GLES20.glDeleteShader(fragment);

            return program;
        }

        private int loadShader(
                int type,
                String source) {

            int shader = GLES20.glCreateShader(type);

            GLES20.glShaderSource(shader, source);

            GLES20.glCompileShader(shader);

            int[] status = new int[1];

            GLES20.glGetShaderiv(
                    shader,
                    GLES20.GL_COMPILE_STATUS,
                    status,
                    0
            );

            if (status[0] == 0) {

                String error =
                        GLES20.glGetShaderInfoLog(shader);

                GLES20.glDeleteShader(shader);

                throw new RuntimeException(
                        "Shader compilation failed: " + error
                );
            }

            return shader;
        }
    }
}