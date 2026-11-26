//! Icons drawn at runtime: a small cut gem in the colour of the state, the
//! same mark as the app's logo. Ruby is off, topaz is working, emerald is
//! protected; needing attention adds a white badge to the ruby gem.

use tray_icon::Icon;

pub const SIZE: u32 = 32;
/// Subsamples per pixel along each axis, for smooth edges.
const SAMPLES: u32 = 4;
/// Gem radius, and the table (inner octagon) relative to it.
const RADIUS: f32 = 15.0;
const TABLE: f32 = 0.56;
/// Facet shading lit from the top, as in the logo: facet 0 is the top,
/// then clockwise. Positive is white, negative is black.
const SHADING: [f32; 8] = [0.32, 0.18, 0.0, -0.16, -0.28, -0.16, 0.0, 0.18];
const TABLE_TINT: f32 = 0.16;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Light {
    Off,
    Busy,
    On,
    Alert,
}

impl Light {
    fn rgb(self) -> [f32; 3] {
        match self {
            Light::Off | Light::Alert => [255.0, 59.0, 107.0],
            Light::Busy => [245.0, 181.0, 68.0],
            Light::On => [25.0, 197.0, 138.0],
        }
    }

    /// Index used by the UI's status colours.
    pub fn kind(self) -> i32 {
        match self {
            Light::Off => 0,
            Light::Busy => 1,
            Light::On => 2,
            Light::Alert => 3,
        }
    }
}

/// Whether (x, y), relative to the centre, lies in a flat-topped octagon
/// of circumradius `r`: its edges face every 45 degrees.
fn in_octagon(x: f32, y: f32, r: f32) -> bool {
    let apothem = r * (22.5_f32).to_radians().cos();
    x.abs() <= apothem
        && y.abs() <= apothem
        && (x.abs() + y.abs()) / std::f32::consts::SQRT_2 <= apothem
}

/// Colour of one subsample, or `None` outside the icon.
fn sample(light: Light, x: f32, y: f32) -> Option<[f32; 3]> {
    let base = light.rgb();
    let centre = SIZE as f32 / 2.0;
    let (dx, dy) = (x - centre, y - centre);

    if light == Light::Alert {
        let (bx, by) = (dx - 9.5, dy - 9.5);
        let d = (bx * bx + by * by).sqrt();
        if d <= 5.5 {
            return Some([255.0; 3]);
        }
        // A clear ring keeps the badge apart from the gem.
        if d <= 7.0 {
            return None;
        }
    }
    if !in_octagon(dx, dy, RADIUS) {
        return None;
    }
    let shade = if in_octagon(dx, dy, RADIUS * TABLE) {
        TABLE_TINT
    } else {
        // Facets meet on the spokes, so the angle picks the facet.
        let angle = dy.atan2(dx).to_degrees() + 90.0;
        SHADING[((angle / 45.0).round() as i32).rem_euclid(8) as usize]
    };
    let target = if shade >= 0.0 { 255.0 } else { 0.0 };
    let t = shade.abs();
    Some(base.map(|c| c + (target - c) * t))
}

/// RGBA pixels of the gem, anti-aliased by supersampling.
pub fn rgba(light: Light) -> Vec<u8> {
    let mut rgba = Vec::with_capacity((SIZE * SIZE * 4) as usize);
    let step = 1.0 / SAMPLES as f32;
    for py in 0..SIZE {
        for px in 0..SIZE {
            let (mut sum, mut hits) = ([0.0f32; 3], 0u32);
            for sy in 0..SAMPLES {
                for sx in 0..SAMPLES {
                    let x = px as f32 + (sx as f32 + 0.5) * step;
                    let y = py as f32 + (sy as f32 + 0.5) * step;
                    if let Some(c) = sample(light, x, y) {
                        for (s, c) in sum.iter_mut().zip(c) {
                            *s += c;
                        }
                        hits += 1;
                    }
                }
            }
            if hits == 0 {
                rgba.extend_from_slice(&[0, 0, 0, 0]);
            } else {
                rgba.extend(sum.map(|s| (s / hits as f32).round() as u8));
                rgba.push((hits * 255 / (SAMPLES * SAMPLES)) as u8);
            }
        }
    }
    rgba
}

pub fn tray_icon(light: Light) -> Icon {
    Icon::from_rgba(rgba(light), SIZE, SIZE).expect("icon dimensions match buffer")
}

#[allow(dead_code)]
pub fn window_icon(light: Light) -> slint::Image {
    let buffer =
        slint::SharedPixelBuffer::<slint::Rgba8Pixel>::clone_from_slice(&rgba(light), SIZE, SIZE);
    slint::Image::from_rgba8(buffer)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn icons_fill_the_buffer_and_have_clear_corners() {
        for light in [Light::Off, Light::Busy, Light::On, Light::Alert] {
            let px = rgba(light);
            assert_eq!(px.len(), (SIZE * SIZE * 4) as usize);
            assert_eq!(px[3], 0, "top-left corner is transparent");
            let centre = ((SIZE / 2 * SIZE + SIZE / 2) * 4) as usize;
            assert_eq!(px[centre + 3], 255, "centre is opaque");
        }
    }

    #[test]
    fn states_differ() {
        assert_ne!(rgba(Light::Off), rgba(Light::On));
        assert_ne!(rgba(Light::Off), rgba(Light::Alert));
        assert_ne!(rgba(Light::Busy), rgba(Light::On));
    }
}
