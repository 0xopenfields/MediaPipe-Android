#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform vec4 u_Color;

void main() {
    gl_FragColor = u_Color;
}