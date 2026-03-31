import { describe, expect, it } from "vitest";
import {
  isNewLineShortcut,
  isSendShortcut,
  isSupportedUploadFile,
  splitSupportedFiles,
} from "./messageInput.behavior";

describe("message input keyboard shortcuts", () => {
  it("sends on Enter without modifiers", () => {
    expect(
      isSendShortcut({
        key: "Enter",
      })
    ).toBe(true);
  });

  it("does not send on Enter with Alt", () => {
    expect(
      isSendShortcut({
        key: "Enter",
        altKey: true,
      })
    ).toBe(false);
  });

  it("allows newline on Alt+Enter", () => {
    expect(
      isNewLineShortcut({
        key: "Enter",
        altKey: true,
      })
    ).toBe(true);
  });

  it("allows newline on Ctrl+Enter", () => {
    expect(
      isNewLineShortcut({
        key: "Enter",
        ctrlKey: true,
      })
    ).toBe(true);
  });
});

describe("message input file support", () => {
  it("accepts supported file types", () => {
    const image = new File(["img"], "photo.png", { type: "image/png" });
    const pdf = new File(["doc"], "spec.pdf", {
      type: "application/pdf",
    });

    expect(isSupportedUploadFile(image)).toBe(true);
    expect(isSupportedUploadFile(pdf)).toBe(true);
  });

  it("rejects unsupported file types", () => {
    const executable = new File(["bin"], "run.exe", {
      type: "application/x-msdownload",
    });

    expect(isSupportedUploadFile(executable)).toBe(false);
  });

  it("splits dropped files into accepted and rejected", () => {
    const ok = new File(["ok"], "ok.txt", { type: "text/plain" });
    const bad = new File(["bad"], "bad.zip", { type: "application/zip" });

    const result = splitSupportedFiles([ok, bad]);
    expect(result.accepted).toHaveLength(1);
    expect(result.rejected).toHaveLength(1);
    expect(result.accepted[0]?.name).toBe("ok.txt");
    expect(result.rejected[0]?.name).toBe("bad.zip");
  });
});
