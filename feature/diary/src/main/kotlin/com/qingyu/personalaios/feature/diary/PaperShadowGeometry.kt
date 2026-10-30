package com.qingyu.personalaios.feature.diary

/** Directional projection onto a real receiver plane. Matrices are column-major. */
internal object PaperShadowGeometry {
    fun projection(px:Float,py:Float,pz:Float,nx:Float,ny:Float,nz:Float,
                   out:FloatArray,lightX:Float=-.12f,lightY:Float=1.05f,lightZ:Float=1f) {
        require(out.size==16)
        val dot=nx*lightX+ny*lightY+nz*lightZ
        require(kotlin.math.abs(dot)>.0001f)
        val offset=-nx*px-ny*py-nz*pz
        for(column in 0..3) {
            val q=when(column) { 0->nx;1->ny;2->nz;else->offset }/dot
            val at=column*4
            out[at]=(if(column==0) 1f else 0f)-lightX*q
            out[at+1]=(if(column==1) 1f else 0f)-lightY*q
            out[at+2]=(if(column==2) 1f else 0f)-lightZ*q
            out[at+3]=if(column==3) 1f else 0f
        }
    }
}
