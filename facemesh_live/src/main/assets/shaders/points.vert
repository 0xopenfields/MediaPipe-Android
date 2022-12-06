#extension GL_OES_EGL_image_external : require

uniform mat4 u_ProjectionMatrix;
uniform float u_PointSize;
attribute vec4 a_Position;

void main() {
    gl_Position = u_ProjectionMatrix * a_Position;
    gl_PointSize = u_PointSize;
}