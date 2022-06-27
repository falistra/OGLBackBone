#version 300 es

in vec2 vPos;
in vec3 aColor;
out vec4 color;

void main(){
color = vec4(aColor,1);
gl_Position = vec4(vPos,0,1);
}