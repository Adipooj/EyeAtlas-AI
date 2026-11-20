# EYEATLAS VR - HARDWARE CONTRACT & ELECTRONICS SPECIFICATION
**Version:** 1.1 | **Controlling Document:** MASTER_BLUEPRINT.md Appendix A (H0-H9) | **Status:** BINDING

## 1. Electrical Architecture: External-Eye Ring Illumination Only
The custom external lighting circuit is strictly restricted to non-retinal, non-flashing, diffused anterior segment and tear-film illumination. It is powered exclusively from an external certified 5.0 V USB-A power bank (no mains charging during patient examinations).

### 1.1 Schematic Authority
```text
CERTIFIED 5V USB POWER BANK (USB-A output; NO mains during use)
 VBUS +5.0 V ----- [F1 resettable polyfuse, I_hold 0.25 A] -- [SW1 ON/OFF]
                    |
                    +-- [R1: 330 ohm, 0.25 W] -->| D1 (Diffused White LED) --+
                    +-- [R2: 330 ohm, 0.25 W] -->| D2 (Diffused White LED) --+
                    +-- [R3: 330 ohm, 0.25 W] -->| D3 (Diffused White LED) --+
                    +-- [R4: 330 ohm, 0.25 W] -->| D4 (Diffused White LED) --+
                    +-- [R5: 330 ohm, 0.25 W] -->| D5 (Diffused White LED) --+
                    +-- [R6: 330 ohm, 0.25 W] -->| D6 (Diffused White LED) --+
                    +-- [R7: 330 ohm, 0.25 W] -->| D7 (Diffused White LED) --+
                    +-- [R8: 330 ohm, 0.25 W] -->| D8 (Diffused White LED) --+
                                                                              |
 GND --------------------------------(return)---------------------------------+
```

### 1.2 Electrical Operating Parameters
- **Supply Voltage (VBUS):** $5.00 \pm 0.25$ V DC.
- **Protection (F1):** Resettable PTC polyfuse, hold current $I_{\text{hold}} = 0.25$ A (250 mA), trip current $I_{\text{trip}} = 0.50$ A.
- **Switch (SW1):** Single-pole single-throw (SPST) mechanical toggle switch, rated $\ge 50$ V / 1 A.
- **LED Branches (D1-D8):** 8 individual, parallel branches. Each LED has its own dedicated 330 $\Omega$ 0.25 W resistor. Common single resistor topologies are strictly prohibited.
- **Nominal Forward Drop ($V_f$):** $3.00$ V ($2.80 - 3.30$ V spread).
- **Per-Branch Current ($I_{\text{branch}}$):**
  $$I = \frac{V_{\text{bus}} - V_f}{R} = \frac{5.00\text{ V} - 3.00\text{ V}}{330\text{ }\Omega} = 6.06\text{ mA}$$
  (Worst-case range: $5.15\text{ mA to }6.67\text{ mA}$).
- **Total System Current ($I_{\text{total}}$):** $8 \times 6.06\text{ mA} = 48.48\text{ mA} \approx 48.5\text{ mA}$.
- **Total Electrical Power:** $P_{\text{total}} = 5.0\text{ V} \times 0.0485\text{ A} = 0.243\text{ W} < 0.25\text{ W}$.
- **Resistor Power Dissipation:** $P_R = I^2 R = (0.00606)^2 \times 330 = 0.0121\text{ W} \ll 0.25\text{ W}$ rating.
- **Operating Temperature Rise:** $< 2.5^\circ\text{C}$ above ambient at equilibrium ($T_{\text{op}} < 32^\circ\text{C}$).

## 2. Tear Optics & Placido Target Assembly
- **Fixture Geometry:** 3D-printed black matte cone hood, non-reflective inner baffle, mounting concentric Placido rings.
- **Fixed Working Distance:** $80.0 \pm 2.0$ mm from camera sensor plane to anterior corneal surface.
- **Optical Separation:** Ring illumination diffuser provides diffuse ring reflection onto cornea; no direct unshielded LED emitters.
- **Corneal Safety:** No mechanical component touches or approaches within 25 mm of the eye. Soft silicone facial spacer prevents accidental contact.
- **Retinal Device Prohibition:** The 5 V external ring circuit is strictly forbidden from coupling into the fundus imaging optical path.

## 3. Retinal Illumination Interface
- **Retinal Light Source:** Factory certified indirect ophthalmoscopy illuminator meeting ISO 15004-2:2024 ophthalmic photochemical and thermal safety limits ($< 450\text{ lux}\cdot\text{s}$).
- **Prohibition:** Improvised smartphone LED flash or direct USB ring LEDs are strictly prohibited for retina imaging.
