precision lowp float;
varying vec4 v_Color;
varying float v_ElapsedTime;
uniform float u_ParticleDistance;

void main()
{
    float xDistance = 0.5 - gl_PointCoord.x;
    float yDistance = 0.5 - gl_PointCoord.y;
    float distanceFromCenter = sqrt(xDistance * xDistance + yDistance * yDistance);

    if (distanceFromCenter > u_ParticleDistance) { discard;
    } else {
        gl_FragColor = vec4(v_Color);
    }
}







