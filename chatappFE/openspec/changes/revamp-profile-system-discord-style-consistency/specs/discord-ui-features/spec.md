## MODIFIED Requirements

### Requirement: Modern light theme styling
All components SHALL use a modern light color scheme with consistent spacing and typography, and high-traffic social surfaces such as the friends page SHALL align visually with the settings page instead of using lower-fidelity placeholder layouts. Profile settings and profile overlays SHALL also follow this same Discord-inspired system-level styling language.

#### Scenario: Color scheme
- **WHEN** viewing any chat component
- **THEN** background is light gray (#f5f5f5) for main area
- **THEN** message bubbles are white with subtle shadow
- **THEN** accent color is blue for buttons and links
- **THEN** text is dark gray (#1a1a1a)

#### Scenario: Component polish
- **WHEN** hovering over interactive elements
- **THEN** smooth hover effects appear (slight color change, shadow)
- **WHEN** opening modals or menus
- **THEN** fade-in animation occurs

#### Scenario: Friends page visual parity with settings
- **WHEN** a user opens the friends page and the settings page in the same product shell
- **THEN** both surfaces SHALL use comparable card treatment, spacing rhythm, and section hierarchy
- **THEN** the friends page SHALL feel like part of the same polished design system rather than an older fallback screen

#### Scenario: Profile surfaces visual parity across settings and overlay cards
- **WHEN** a user compares profile settings and profile overlay cards opened from avatar/name/mention
- **THEN** those surfaces SHALL use the same visual language for card depth, typography hierarchy, spacing rhythm, and action emphasis
- **THEN** profile overlays SHALL feel like a coherent extension of the profile settings experience
