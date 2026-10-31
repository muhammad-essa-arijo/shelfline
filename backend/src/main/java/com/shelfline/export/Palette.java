package com.shelfline.export;

/** Colours used in the PDF and Excel files. They match the ShelfLine design system. */
final class Palette {

    static final int TEAL = 0x0F766E;
    static final int TINT = 0xF0FDFA;
    static final int BORDER = 0xE2E8F0;
    static final int STRONG = 0x0F172A;
    static final int BODY = 0x334155;
    static final int MUTED = 0x64748B;
    static final int ARROW = 0x94A3B8;
    static final int WHITE = 0xFFFFFF;

    /** Text, background and border colour of one status. */
    record StatusColors(int text, int bg, int border) {
    }

    private static final StatusColors SAFE = new StatusColors(0x166534, 0xDCFCE7, 0x86EFAC);
    private static final StatusColors SOON = new StatusColors(0x92400E, 0xFEF3C7, 0xFCD34D);
    private static final StatusColors EXPIRED = new StatusColors(0x991B1B, 0xFEE2E2, 0xFCA5A5);
    private static final StatusColors LOW = new StatusColors(0x9A3412, 0xFFEDD5, 0xFDBA74);

    private Palette() {
    }

    /** status is SAFE, EXPIRES_SOON, EXPIRED or LOW. */
    static StatusColors forStatus(String status) {
        switch (status) {
            case "EXPIRES_SOON":
                return SOON;
            case "EXPIRED":
                return EXPIRED;
            case "LOW":
                return LOW;
            default:
                return SAFE;
        }
    }
}
