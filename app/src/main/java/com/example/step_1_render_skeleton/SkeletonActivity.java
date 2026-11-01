package com.example.step_1_render_skeleton;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.view.MotionEvent;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.os.Handler;
import android.os.Looper;

public class SkeletonActivity extends Activity {

    private GLSurfaceView glView;
    private SkeletonGLView skeletonView;

    private Handler animationHandler =
            new Handler(Looper.getMainLooper());

    private boolean animationRunning = false;

    private float animationTime = 0.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        skeletonView = new SkeletonGLView(this);
        glView = skeletonView;

        setContentView(glView);

        animationRunning = true;

        animationHandler.post(animationRunnable);
    }

    public void updateSkeleton(float[][] newPositions) {
        if (skeletonView != null) {
            skeletonView.updateSkeleton(newPositions);
        }
    }

    private void testSkeletonMovement() {

        float[][] positions = {
                {0.0f, 2.2f, 0.0f},
                {0.0f, 1.7f, 0.0f},
                {0.0f, 1.0f, 0.0f},
                {0.0f, 0.0f, 0.0f},
                {-0.9f, 1.2f, 0.0f},
                {-1.5f, 0.5f, 0.0f},
                {0.9f, 1.2f, 0.0f},
                {1.5f, 0.5f, 0.0f},
                {-0.5f, -1.2f, 0.0f},
                {0.5f, -1.2f, 0.0f}
        };

        updateSkeleton(positions);
    }

    private final Runnable animationRunnable = new Runnable() {

        @Override
        public void run() {

            if (!animationRunning) {
                return;
            }

            animationTime += 0.08f;

            float swing = (float) Math.sin(animationTime);

            float legSwing = swing * 0.5f;
            float armSwing = swing * 0.4f;

            float[][] positions = {

                    {0.0f, 2.2f, 0.0f},  // Head
                    {0.0f, 1.7f, 0.0f},  // Neck
                    {0.0f, 1.0f, 0.0f},  // Chest
                    {0.0f, 0.0f, 0.0f},  // Pelvis

                    {-0.9f, 1.2f, 0.0f},  // Left shoulder
                    {-1.5f, 0.5f + armSwing, 0.0f}, // Left hand

                    {0.9f, 1.2f, 0.0f},   // Right shoulder
                    {1.5f, 0.5f - armSwing, 0.0f}, // Right hand

                    {-0.5f, -1.2f + legSwing, 0.0f}, // Left foot
                    {0.5f, -1.2f - legSwing, 0.0f}   // Right foot
            };

            updateSkeleton(positions);

            animationHandler.postDelayed(this, 33);
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        if (glView != null) glView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (glView != null) glView.onResume();
    }

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

        public void updateSkeleton(final float[][] positions) {

            if (positions == null || positions.length != 10) {
                throw new IllegalArgumentException(
                        "Skeleton must contain exactly 10 joints."
                );
            }

            final float[][] copy = new float[10][3];

            for (int i = 0; i < 10; i++) {

                if (positions[i] == null ||
                        positions[i].length != 3) {
                    throw new IllegalArgumentException(
                            "Each joint needs X, Y and Z."
                    );
                }

                for (int j = 0; j < 3; j++) {

                    float value = positions[i][j];

                    if (Float.isNaN(value) ||
                            Float.isInfinite(value)) {
                        throw new IllegalArgumentException(
                                "Coordinates must be finite."
                        );
                    }

                    copy[i][j] = value;
                }
            }

            queueEvent(new Runnable() {
                @Override
                public void run() {
                    renderer.updateSkeleton(copy);
                }
            });
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

                    final float dx = x - previousX;
                    final float dy = y - previousY;

                    previousX = x;
                    previousY = y;

                    queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            renderer.rotate(dx, dy);
                        }
                    });

                    return true;

                case MotionEvent.ACTION_UP:
                    return true;
            }

            return true;
        }
    }

    private static class SkeletonRenderer
            implements GLSurfaceView.Renderer {

        private int program;

        private int positionHandle;
        private int colorHandle;
        private int mvpHandle;
        private int pointSizeHandle;

        private FloatBuffer jointBuffer;
        private FloatBuffer boneBuffer;

        private final float[] projectionMatrix = new float[16];
        private final float[] viewMatrix = new float[16];
        private final float[] modelMatrix = new float[16];
        private final float[] mvpMatrix = new float[16];

        private float rotationX = 0;
        private float rotationY = 0;

        private final float[][] joints = {

                {0.0f, 2.0f, 0.0f},
                {0.0f, 1.6f, 0.0f},
                {0.0f, 0.9f, 0.0f},
                {0.0f, 0.0f, 0.0f},

                {-0.7f, 1.1f, 0.0f},
                {-1.2f, 0.3f, 0.0f},

                {0.7f, 1.1f, 0.0f},
                {1.2f, 0.3f, 0.0f},

                {-0.4f, -1.0f, 0.0f},
                {0.4f, -1.0f, 0.0f}
        };

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

        private final String vertexShader =
                "uniform mat4 uMVPMatrix;" +
                        "uniform float uPointSize;" +
                        "attribute vec4 aPosition;" +
                        "void main() {" +
                        "gl_Position = uMVPMatrix * aPosition;" +
                        "gl_PointSize = uPointSize;" +
                        "}";

        private final String fragmentShader =
                "precision mediump float;" +
                        "uniform vec4 uColor;" +
                        "void main() {" +
                        "gl_FragColor = uColor;" +
                        "}";

        @Override
        public void onSurfaceCreated(
                GL10 gl, EGLConfig config) {

            GLES20.glClearColor(
                    0.06f, 0.07f, 0.10f, 1.0f
            );

            GLES20.glEnable(GLES20.GL_DEPTH_TEST);

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

        public void updateSkeleton(float[][] newPositions) {

            for (int i = 0; i < 10; i++) {
                System.arraycopy(
                        newPositions[i],
                        0,
                        joints[i],
                        0,
                        3
                );
            }

            // Buffers may not exist during startup.
            if (jointBuffer == null || boneBuffer == null) {
                return;
            }

            updateBuffers();
        }

        private void updateBuffers() {

            if (jointBuffer == null || boneBuffer == null) {
                return;
            }

            jointBuffer.position(0);

            for (int i = 0; i < 10; i++) {
                jointBuffer.put(joints[i]);
            }

            jointBuffer.position(0);

            boneBuffer.position(0);

            for (int[] bone : bones) {
                for (int jointID : bone) {
                    boneBuffer.put(joints[jointID]);
                }
            }

            boneBuffer.position(0);
        }
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

            boneBuffer = createFloatBuffer(boneVertices);

            float[] jointVertices =
                    new float[joints.length * 3];

            index = 0;

            for (float[] joint : joints) {
                for (float coordinate : joint) {
                    jointVertices[index++] = coordinate;
                }
            }

            jointBuffer = createFloatBuffer(jointVertices);
        }

        private FloatBuffer createFloatBuffer(float[] values) {

            ByteBuffer bb = ByteBuffer.allocateDirect(
                    values.length * 4
            );

            bb.order(ByteOrder.nativeOrder());

            FloatBuffer buffer = bb.asFloatBuffer();

            buffer.put(values);
            buffer.position(0);

            return buffer;
        }

        @Override
        public void onSurfaceChanged(
                GL10 gl, int width, int height) {

            GLES20.glViewport(0, 0, width, height);

            if (width <= 0 || height <= 0) return;

            float ratio = (float) width / (float) height;

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

        @Override
        public void onDrawFrame(GL10 gl) {

            GLES20.glClear(
                    GLES20.GL_COLOR_BUFFER_BIT |
                            GLES20.GL_DEPTH_BUFFER_BIT
            );

            if (program == 0 ||
                    jointBuffer == null ||
                    boneBuffer == null) {
                return;
            }

            GLES20.glUseProgram(program);

            Matrix.setIdentityM(modelMatrix, 0);

            Matrix.rotateM(
                    modelMatrix, 0,
                    rotationX, 1, 0, 0
            );

            Matrix.rotateM(
                    modelMatrix, 0,
                    rotationY, 0, 1, 0
            );

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

            GLES20.glEnableVertexAttribArray(
                    positionHandle
            );

            // Draw bones.

            GLES20.glUniform4f(
                    colorHandle,
                    0.85f, 0.85f, 0.90f, 1.0f
            );

            GLES20.glUniform1f(
                    pointSizeHandle, 1.0f
            );

            GLES20.glLineWidth(2.0f);

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

            // Draw joints.

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
        public void rotate(float dx, float dy) {

            rotationY += dx * 0.5f;
            rotationX += dy * 0.5f;
        }

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
                GLES20.glDeleteShader(vertex);
                GLES20.glDeleteShader(fragment);

                throw new RuntimeException(
                        "Program linking failed: " + error
                );
            }

            GLES20.glDeleteShader(vertex);
            GLES20.glDeleteShader(fragment);

            return program;
        }

        private int loadShader(int type, String source) {

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