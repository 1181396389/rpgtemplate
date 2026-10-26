#version 150
uniform sampler2D SceneColor;
uniform float Time;
uniform float Mode;
uniform float Intensity;
uniform float UseScene;
uniform vec4 Viewport;
in vec4 vertexColor;
in vec2 uv;
out vec4 fragColor;
const float PI=3.14159265359;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);}
vec3 spectrum(float t){return .55+.45*cos(6.28318*(vec3(0,.33,.67)+t));}
void main(){
 if(Mode<.5){float edge=pow(max(0.0,1.0-abs(uv.y*2.0-1.0)),.65);float flow=.68+.32*sin(uv.x*25.0-Time*9.0);fragColor=vec4(vertexColor.rgb*(.7+flow*.6),vertexColor.a*edge*Intensity);return;}
 vec2 p=uv*2.0-1.0;float r=length(p);if(r>1.0)discard;float angle=r>.00001?atan(p.y,p.x):0.0;float fade=1.0-smoothstep(.78,1.0,r);
 if(Mode<1.5){
  float horizon=.235;float bend=.012/(abs(r-horizon)+.09);vec2 dir=p/max(r,.001);vec2 screen=(gl_FragCoord.xy-Viewport.xy)/max(Viewport.zw,vec2(1));
  vec3 background=vec3(.012,.007,.023);if(UseScene>.5)background=texture(SceneColor,clamp(screen+dir*bend*.22,vec2(.002),vec2(.998))).rgb;
  float n=noise(vec2(angle*6.0-Time*1.4,r*26.0+Time*.55));float photon=exp(-abs(r-.27)*85.0);float halo=exp(-abs(r-.36)*17.0)*(.4+.6*n);
  float disk=exp(-abs(p.y+.07*sin(p.x*9.0+Time*2.0))*24.0)*smoothstep(.23,.35,abs(p.x))*(1.0-smoothstep(.55,.98,abs(p.x)));
  vec3 emission=vec3(.65,.22,1.0)*halo*.7+vec3(.95,.8,1.0)*photon+vec3(1.0,.53,.18)*disk*(.65+n);
  float body=smoothstep(horizon-.012,horizon+.012,r);vec3 color=background*body+emission;float alpha=fade*vertexColor.a*Intensity;fragColor=vec4(color,alpha);return;
 }
 float swirl=noise(vec2(angle*4.0+Time*.4,r*18.0-Time*2.0));float rings=pow(.5+.5*sin(r*56.0-Time*6.0+swirl*3.0),12.0);float star=pow(abs(cos(angle*6.0+Time*.2)),24.0)*exp(-r*2.2);float core=exp(-r*7.0);vec3 c=spectrum(angle/(2.0*PI)+Time*.08)*(.4+rings*.9)+vec3(core+star*.65);float alpha=(rings*.5+core*.8+star*.6)*fade*vertexColor.a;fragColor=vec4(c*Intensity,clamp(alpha,0.0,1.0));
}
