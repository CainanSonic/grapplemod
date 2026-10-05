package bat.grapnel.mod;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public class GrapnelMarkerParticle extends TextureSheetParticle {
    
    protected GrapnelMarkerParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        
        // --- TEXTURE CONFIGURATION ---
        this.setSpriteFromAge(sprites);
        
        // --- ARKHAM UI PHYSICS ---
        this.xd = 0; // Completely freeze X movement
        this.yd = 0; // Completely freeze Y movement
        this.zd = 0; // Completely freeze Z movement
        this.hasPhysics = false; // Ignore gravity and collisions
        
        // --- SIZE & SCALE ---
        this.quadSize = 1.0F; // Adjust this scale! 1.0F makes it roughly 1 full block wide/tall
        
        // --- ANTI-SPAM LIFETIME ---
        this.lifetime = 1; // Dies after 1 ticks. This keeps exactly ONE icon alive at a time!
    }

    @Override
    public ParticleRenderType getRenderType() {
        // Allows alpha transparency for clean shapes/edges in your PNG
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; 
    }

    // This provider wires your textures directly to the particle
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, 
                                       double x, double y, double z, 
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new GrapnelMarkerParticle(level, x, y, z, this.sprites);
        }
    }
}
