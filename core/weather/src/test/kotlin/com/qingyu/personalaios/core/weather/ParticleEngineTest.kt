package com.qingyu.personalaios.core.weather

import com.qingyu.personalaios.domain.*
import org.junit.Assert.*
import org.junit.Test

class ParticleEngineTest {
    private val config = ParticleConfig(ParticleType.SNOW, 140, .7f, 4.6f, .025f, .09f, .55f, .016f)
    @Test fun reproducibleSeedsSpanAllDepthsAndStayWithinBudget() {
        val engine = ParticleEngine(config)
        assertEquals(engine.particles, ParticleEngine(config).particles)
        assertEquals(setOf(0, 1, 2), engine.particles.map { it.depth }.toSet())
        assertTrue(engine.particles.all { it.size in config.minSize..config.maxSize && it.speed in config.minSpeed..config.maxSpeed })
    }
    @Test fun positionsWrapForLongRunningAndNegativeWind() {
        val engine = ParticleEngine(config.copy(windX = -.3f))
        engine.particles.forEach { particle ->
            listOf(0f, 1f, 60f, 3600f, 86400f).forEach { time ->
                assertTrue(engine.x(particle, time) in -.081f..1.081f)
                assertTrue(engine.y(particle, time) in -.081f..1.081f)
            }
        }
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnboundedParticleBudget() { ParticleEngine(config.copy(count = 100_000)) }
}
