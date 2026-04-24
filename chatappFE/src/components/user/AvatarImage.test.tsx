// @vitest-environment jsdom

import { describe, expect, it } from "vitest";
import { render } from "@testing-library/react";

import AvatarImage from "./AvatarImage";

describe("AvatarImage", () => {
  it("keeps the same content-box size for fallback and resolved avatar sources", () => {
    const fallbackView = render(
      <AvatarImage src="/default-avatar.png" alt="fallback avatar" size={32} />
    );
    const resolvedView = render(
      <AvatarImage src="/avatars/generated-u1.png" alt="resolved avatar" size={32} />
    );

    const fallbackBox = fallbackView.getByAltText("fallback avatar").parentElement as HTMLElement;
    const resolvedBox = resolvedView.getByAltText("resolved avatar").parentElement as HTMLElement;

    expect(fallbackBox.style.width).toBe("32px");
    expect(fallbackBox.style.height).toBe("32px");
    expect(resolvedBox.style.width).toBe("32px");
    expect(resolvedBox.style.height).toBe("32px");
  });

  it("applies representative shared avatar sizes without changing the content-box contract", () => {
    const sizes = [16, 24, 28, 32, 40, 44];

    sizes.forEach((size) => {
      const view = render(
        <AvatarImage src="/default-avatar.png" alt={`avatar-${size}`} size={size} />
      );

      const box = view.getByAltText(`avatar-${size}`).parentElement as HTMLElement;
      expect(box.style.width).toBe(`${size}px`);
      expect(box.style.height).toBe(`${size}px`);
    });
  });
});