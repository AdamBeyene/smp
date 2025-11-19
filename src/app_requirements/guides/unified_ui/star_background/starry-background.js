/**
 * Starry Background Animation
 * Creates a slowly moving starfield effect similar to Grok's interface
 * Stars fade in gradually after page load
 */

class StarryBackground {
    constructor(options = {}) {
        this.starCount = options.starCount || 150;
        this.fadeInDuration = options.fadeInDuration || 3000; // 3 seconds
        this.minDelay = options.minDelay || 0;
        this.maxDelay = options.maxDelay || 2000;
        this.cometCount = options.cometCount || 2; // Max 2 comets per interval
        this.cometInterval = options.cometInterval || 30000; // 30 seconds
        this.starModeOffOn = options.starModeOffOn !== undefined ? options.starModeOffOn : true; // Default ON
        this.idleDelay = options.idleDelay || 17000; // 17 seconds idle time
        this.container = null;
        this.stars = [];
        this.comets = [];
        this.cometTimer = null;
        this.activityTimer = null;
        this.starsHidden = false; // Track whether stars are currently hidden
        this.activeCometDirections = []; // Track active comet directions to avoid duplicates

        // Circular drift center (closer to bottom-right page corner)
        // Moved closer to the actual corner for more visible circular motion
        this.driftCenterX = 115; // 115% of viewport width (just off right edge)
        this.driftCenterY = 115; // 115% of viewport height (just off bottom edge)

        this.init();
    }

    init() {
        // Create canvas container
        this.container = document.createElement('div');
        this.container.id = 'starry-canvas';
        document.body.insertBefore(this.container, document.body.firstChild);

        // Generate stars after a brief delay to ensure page loads with black bg
        setTimeout(() => {
            this.generateStars();
            // Start comet generation after stars are visible
            setTimeout(() => {
                this.startCometGeneration();
            }, this.fadeInDuration + 2000);
        }, 100);

        // Set up activity detection and visibility API
        this.setupActivityDetection();
        this.setupVisibilityAPI();
    }

    generateStars() {
        console.log(`🌟 Generating ${this.starCount} stars...`);
        for (let i = 0; i < this.starCount; i++) {
            this.createStar();
        }
        console.log(`✅ Created ${this.stars.length} stars with animations`);
    }

    createStar() {
        const star = document.createElement('div');
        star.className = 'star';
        
        // Random position
        const x = Math.random() * 100;
        const y = Math.random() * 100;
        star.style.left = `${x}%`;
        star.style.top = `${y}%`;
        star.style.position = 'absolute';

        // Random size (small, medium, large)
        const sizeRandom = Math.random();
        if (sizeRandom < 0.7) {
            star.classList.add('small');
        } else if (sizeRandom < 0.9) {
            star.classList.add('medium');
        } else {
            star.classList.add('large');
        }

        // Random twinkle duration (slower = more subtle)
        const twinkleDuration = 2 + Math.random() * 4; // 2-6 seconds
        
        // Random delay for staggered fade-in effect
        const fadeInDelay = Math.random() * (this.maxDelay - this.minDelay) + this.minDelay;
        const fadeInDurationSeconds = this.fadeInDuration / 1000; // Convert ms to seconds
        
        // Random delay for rainbow color shift (0-60s) so stars don't all change color simultaneously
        const rainbowDelay = -(Math.random() * 60000); // Negative delay in ms to start at different points
        
        // Circular orbit effect - continuous clockwise rotation around off-screen center (bottom-right)
        const starX = parseFloat(star.style.left);
        const starY = parseFloat(star.style.top);

        // Calculate distance from star to orbital center (150%, 150%)
        const dx = this.driftCenterX - starX;
        const dy = this.driftCenterY - starY;
        const orbitalRadius = Math.sqrt(dx * dx + dy * dy);

        // Calculate initial angle of star relative to center (for starting position)
        const initialAngle = Math.atan2(dy, dx) * 180 / Math.PI;

        // Set transform-origin to point to the orbital center
        // Use viewport units (vw/vh) so rotation center is at the correct screen position
        const originX = dx; // Distance to center in viewport percentage
        const originY = dy;
        star.style.setProperty('--origin-x', `${originX}vw`);
        star.style.setProperty('--origin-y', `${originY}vh`);
        star.style.setProperty('--initial-angle', `${initialAngle}deg`);

        // Orbital period based on distance - extremely slow for barely perceptible drift
        // Farther stars orbit slower (300-600 seconds = 5-10 minutes!)
        const orbitalPeriod = 300 + (orbitalRadius * 3); // 300-600 seconds based on distance

        // NEGATIVE animation delay to start stars at different points in their orbit
        // This spreads them out immediately without making them wait/sit still
        const orbitDelay = -(Math.random() * orbitalPeriod * 1000); // Negative delay in ms

        // Set complete animation property with all four animations
        // Negative delays make stars start mid-orbit and at different rainbow phases
        const animationString = `twinkle ${twinkleDuration}s linear infinite, fadeIn ${fadeInDurationSeconds}s ease-out ${fadeInDelay}ms forwards, orbit ${orbitalPeriod}s linear ${orbitDelay}ms infinite, rainbowShift 60s linear ${rainbowDelay}ms infinite`;
        star.style.animation = animationString;

        // Debug: Log first star's animation and orbital values
        if (this.stars.length === 0) {
            console.log('First star animation:', animationString);
            console.log('First star orbit:', `Radius: ${orbitalRadius.toFixed(1)}vw, Angle: ${initialAngle.toFixed(1)}°, Period: ${orbitalPeriod.toFixed(1)}s`);
            console.log('Transform origin:', star.style.getPropertyValue('--origin-x'), star.style.getPropertyValue('--origin-y'));
        }

        this.container.appendChild(star);
        this.stars.push(star);
    }

    startCometGeneration() {
        console.log(`☄️ Starting comet generation (${this.cometCount} comets every ${this.cometInterval/1000}s)`);
        
        // Generate initial comets
        this.generateComets();
        
        // Set up recurring comet generation
        this.cometTimer = setInterval(() => {
            this.generateComets();
        }, this.cometInterval);
    }

    generateComets() {
        // Generate random number of comets (1 to cometCount)
        const numComets = Math.floor(Math.random() * this.cometCount) + 1;
        console.log(`☄️ Generating ${numComets} comet(s)...`);
        
        // Clear previous directions tracking
        this.activeCometDirections = [];
        
        for (let i = 0; i < numComets; i++) {
            // Stagger comet appearance significantly to avoid simultaneous comets
            setTimeout(() => {
                this.createComet();
            }, i * 3000); // 3 seconds apart instead of 0.5 seconds
        }
    }

    createComet() {
        const comet = document.createElement('div');
        comet.className = 'comet';
        
        // Random rainbow color for this comet (static, not animated)
        const rainbowColors = [
            '#ff6b6b', // Red
            '#ffa500', // Orange
            '#ffd700', // Yellow
            '#00ff7f', // Green
            '#1e90ff', // Blue
            '#9370db', // Indigo
            '#ff69b4'  // Violet/Pink
        ];
        const randomColor = rainbowColors[Math.floor(Math.random() * rainbowColors.length)];
        comet.style.setProperty('--comet-color', randomColor);
        
        // Generate unique trajectory
        let edge, startX, startY, endX, endY, directionKey;
        let attempts = 0;
        const maxAttempts = 20;
        
        do {
            // Random starting edge
            edge = Math.floor(Math.random() * 4); // 0=top, 1=right, 2=bottom, 3=left
            
            switch(edge) {
                case 0: // Top edge - travels downward
                    startX = 20 + Math.random() * 60; // Avoid extreme edges
                    startY = -5;
                    endX = startX + (Math.random() - 0.5) * 60; // Diagonal variation
                    endY = 105;
                    break;
                case 1: // Right edge - travels leftward
                    startX = 105;
                    startY = 20 + Math.random() * 60;
                    endX = -5;
                    endY = startY + (Math.random() - 0.5) * 60;
                    break;
                case 2: // Bottom edge - travels upward
                    startX = 20 + Math.random() * 60;
                    startY = 105;
                    endX = startX + (Math.random() - 0.5) * 60;
                    endY = -5;
                    break;
                case 3: // Left edge - travels rightward
                    startX = -5;
                    startY = 20 + Math.random() * 60;
                    endX = 105;
                    endY = startY + (Math.random() - 0.5) * 60;
                    break;
            }
            
            // Create direction key (rounded to avoid exact duplicates but catch similar ones)
            directionKey = `${edge}-${Math.round(startX/10)}-${Math.round(startY/10)}`;
            attempts++;
            
        } while (this.activeCometDirections.includes(directionKey) && attempts < maxAttempts);
        
        // Track this direction
        this.activeCometDirections.push(directionKey);
        
        comet.style.left = `${startX}%`;
        comet.style.top = `${startY}%`;
        
        // Calculate direction for trail (same as movement direction)
        const angle = Math.atan2(endY - startY, endX - startX);
        const trailAngle = angle * 180 / Math.PI; // Same direction as movement
        
        // Calculate travel distance in viewport units
        // Using Pythagorean theorem: distance = sqrt((x2-x1)^2 + (y2-y1)^2)
        const deltaX = endX - startX;
        const deltaY = endY - startY;
        const travelDistance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        
        comet.style.setProperty('--trail-angle', `${trailAngle}deg`);
        comet.style.setProperty('--travel-distance', `${travelDistance}vw`);
        
        // Random speed (2-5 seconds) - affects brightness perception
        const duration = 2 + Math.random() * 3;
        comet.style.animationDuration = `${duration}s`;

        // Random brightness variation (0.6 to 1.0)
        const brightness = 0.6 + Math.random() * 0.4;
        comet.style.setProperty('--comet-brightness', brightness);

        // Trail length varies with speed (faster = longer trail perception)
        // Reduced by 15% for more delicate effect (85-127.5 pixels)
        const trailLength = 85 + (5 - duration) * 13.6; // Faster comets have longer trails (85-127.5px)
        comet.style.setProperty('--trail-length', `${trailLength}px`);
        
        console.log(`☄️ Comet: edge=${edge}, color=${randomColor}, angle=${trailAngle.toFixed(1)}°, distance=${travelDistance.toFixed(1)}vw, speed=${duration.toFixed(1)}s, brightness=${brightness.toFixed(2)}, trail=${trailLength.toFixed(0)}px`);
        
        this.container.appendChild(comet);
        this.comets.push(comet);
        
        // Remove comet after animation completes and clear from tracking
        setTimeout(() => {
            if (comet.parentNode) {
                comet.parentNode.removeChild(comet);
            }
            const index = this.comets.indexOf(comet);
            if (index > -1) {
                this.comets.splice(index, 1);
            }
            // Remove from active directions
            const dirIndex = this.activeCometDirections.indexOf(directionKey);
            if (dirIndex > -1) {
                this.activeCometDirections.splice(dirIndex, 1);
            }
        }, duration * 1000 + 100);
    }

    destroy() {
        if (this.cometTimer) {
            clearInterval(this.cometTimer);
        }
        if (this.activityTimer) {
            clearTimeout(this.activityTimer);
        }
        if (this.container && this.container.parentNode) {
            this.container.parentNode.removeChild(this.container);
        }
        this.stars = [];
        this.comets = [];
    }

    /**
     * Set up activity detection to hide stars during user interaction
     * Only mouse clicks and keyboard clicks trigger hiding
     */
    setupActivityDetection() {
        if (!this.starModeOffOn) {
            return; // Mode is OFF, stars always visible
        }

        // Only mouse clicks and keyboard clicks
        const activityEvents = ['click', 'keydown'];

        const handleActivity = () => {
            // Only hide and start timer if stars are currently visible
            if (!this.starsHidden) {
                this.starsHidden = true;
                this.container.style.opacity = '0';
                console.log('⚡ Click detected, hiding stars');
            }

            // Always reset the idle timer
            if (this.activityTimer) {
                clearTimeout(this.activityTimer);
            }

            // Set new timer to show stars after idle period
            this.activityTimer = setTimeout(() => {
                this.starsHidden = false;
                this.container.style.opacity = '1';
                console.log('💤 No clicks for 17s, showing stars');
            }, this.idleDelay);
        };

        activityEvents.forEach(event => {
            document.addEventListener(event, handleActivity, { passive: true });
        });

        console.log(`⚡ Activity detection enabled (idle delay: ${this.idleDelay}ms, only click/keydown)`);
    }

    /**
     * Set up Page Visibility API to hide stars when tab is inactive
     */
    setupVisibilityAPI() {
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                this.container.style.opacity = '0';
                this.starsHidden = true;
                console.log('👁️ Tab hidden, stars paused');
            } else {
                // Only show if mode is OFF
                if (!this.starModeOffOn) {
                    this.container.style.opacity = '1';
                    this.starsHidden = false;
                }
                console.log('👁️ Tab visible, stars resumed');
            }
        });
    }

    /**
     * Toggle starModeOffOn on/off
     * @returns {boolean} New mode state (true = ON, false = OFF)
     */
    toggleMode() {
        this.starModeOffOn = !this.starModeOffOn;

        if (this.starModeOffOn) {
            // Mode turned ON - enable activity detection
            console.log('⭐ Star mode: ON (hide on activity)');
            this.setupActivityDetection();
        } else {
            // Mode turned OFF - show stars always
            console.log('⭐ Star mode: OFF (always visible)');
            this.container.style.opacity = '1';
            this.starsHidden = false;
            if (this.activityTimer) {
                clearTimeout(this.activityTimer);
            }
        }

        return this.starModeOffOn;
    }
}

// Auto-initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        window.starryBackground = new StarryBackground({
            starCount: 400,       // Increased from 300 (added 150 more)
            fadeInDuration: 3000,
            minDelay: 0,
            maxDelay: 2000,
            cometCount: 2,        // Max 2 comets per interval
            cometInterval: 30000, // Every 30 seconds
            starModeOffOn: true,  // Hide stars on activity (default ON)
            idleDelay: 17000      // Show stars after 17s idle
        });
    });
} else {
    window.starryBackground = new StarryBackground({
        starCount: 400,       // Increased from 300 (added 100 more)
        fadeInDuration: 3000,
        minDelay: 0,
        maxDelay: 2000,
        cometCount: 2,        // Max 2 comets per interval
        cometInterval: 30000, // Every 30 seconds
        starModeOffOn: true,  // Hide stars on activity (default ON)
        idleDelay: 17000      // Show stars after 17s idle
    });
}
