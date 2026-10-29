"""Flesh texture for the 一无所有 cocoon egg (茧 / 蛋).

128x128 RGB, periodic in u (the egg is wrapped around its vertical axis), so the seam does not show.
  - mottled flesh tone (low-frequency noise, multiplicative: 255 = the vertex colour unchanged)
  - wrinkles: long horizontal folds (stretched along u, thin in v), darker in the creases, a lighter sheen on
    the ridges, matching the horizontal folds of the drawing
  - fine vein / pore noise
  - the bottom-right 4x4 block is pure white: the eyes sample it, so they keep their vertex colours.
Output: build0414-resources/assets/rpgtemplate/textures/entity/nt_egg.png
"""
import os
import numpy as np
from PIL import Image

OUT = '/home/user/mod-repo/build0414-resources/assets/rpgtemplate/textures/entity/nt_egg.png'
N = 128
rng = np.random.default_rng(20261028)
fy = np.fft.fftfreq(N)[:, None]
fx = np.fft.fftfreq(N)[None, :]


def periodic_noise(sigma_u, sigma_v):
    """Gaussian-filtered white noise, periodic in both axes (FFT), normalised to mean 0, std 1."""
    white = rng.standard_normal((N, N))
    H = np.exp(-2.0 * np.pi ** 2 * ((fx * sigma_u) ** 2 + (fy * sigma_v) ** 2))
    out = np.real(np.fft.ifft2(np.fft.fft2(white) * H))
    return (out - out.mean()) / (out.std() + 1e-9)


mottle = periodic_noise(7.0, 7.0)
folds = periodic_noise(10.0, 2.6)          # long in u, thin in v: horizontal wrinkles
veins = periodic_noise(1.0, 1.0)           # fine vein / pore grain

crease = np.exp(-(folds / 0.45) ** 2)      # thin dark lines where the fold field crosses zero
sheen = np.exp(-((folds - 1.1) / 0.45) ** 2)  # lighter ridge tops beside the creases

mod = 0.95 + 0.09 * mottle
mod = mod * (1.0 - 0.22 * crease)
mod = mod * (1.0 + 0.06 * sheen)
mod = mod * (1.0 + 0.03 * veins)

# warm flesh tint: red stays dominant, green and blue fall off a little in the creases
r = np.clip(mod * 1.00, 0, 1)
g = np.clip(mod * (0.90 + 0.10 * (1.0 - crease)), 0, 1)
b = np.clip(mod * (0.90 + 0.10 * (1.0 - crease)), 0, 1)
img = np.stack([r, g, b], axis=-1)
img = (img * 255.0).round().astype(np.uint8)
img[N - 4:, N - 4:, :] = 255               # eye block: pure white

os.makedirs(os.path.dirname(OUT), exist_ok=True)
Image.fromarray(img, 'RGB').save(OUT)
print('saved', OUT, img.shape, 'mean', img[..., 0].mean().round(1), img[..., 1].mean().round(1))
