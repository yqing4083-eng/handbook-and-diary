package com.qingyu.personalaios.feature.diary

/** Convex planar faces and their extrusion share precisely the same cached rim.
 * Independent face strips/outline sampling left crossing slivers at the corners.
 * Rigid paper needs no interior subdivision: a centre fan preserves its shape. */
internal class BookMeshGeometry(width:Float,height:Float,radius:Float,side:Int=0,rimDepth:Float=0f) {
    val vertices:FloatArray
    val indices:ShortArray
    init {
        require(width>0f && height>0f && rimDepth>=0f)
        val outline=BookPlane(width,height,radius,straightEdge=if(side>0) -1 else if(side<0) 1 else 0).perimeter
        val count=outline.size
        if(rimDepth>0f) {
            vertices=FloatArray(count*2*5);indices=ShortArray(count*6)
            outline.forEachIndexed { i,(x,y) -> for(j in 0..1) {
                val k=(i*2+j)*5
                vertices[k]=x;vertices[k+1]=y;vertices[k+2]=-rimDepth*j
                vertices[k+3]=0f;vertices[k+4]=j.toFloat()
            } }
            outline.indices.forEach { i -> val a=i*2;val b=((i+1)%count)*2
                intArrayOf(a,a+1,b,b,a+1,b+1).forEachIndexed { j,v -> indices[i*6+j]=v.toShort() }
            }
        } else {
            vertices=FloatArray((count+1)*5);indices=ShortArray(count*3)
            vertices[3]=.5f;vertices[4]=.5f
            outline.forEachIndexed { i,(x,y) ->
                val k=(i+1)*5
                vertices[k]=x;vertices[k+1]=y
                vertices[k+3]=x/width+.5f;vertices[k+4]=.5f-y/height
                indices[i*3]=0;indices[i*3+1]=(i+1).toShort();indices[i*3+2]=((i+1)%count+1).toShort()
            }
        }
    }
}
