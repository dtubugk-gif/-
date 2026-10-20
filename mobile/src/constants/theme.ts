import { useColorScheme } from "react-native";

export const Palette = {
  light: {
    bg: "#F6F7FB",
    card: "#FFFFFF",
    text: "#0B1020",
    muted: "#5B6275",
    border: "#E3E6EF",
    primary: "#6B4EFF",
    primaryText: "#FFFFFF",
    success: "#0E9F6E",
    warning: "#C27803",
    danger: "#E02424",
    chip: "#EEEAFE",
  },
  dark: {
    bg: "#0B1020",
    card: "#151B2E",
    text: "#F4F6FB",
    muted: "#9AA3B8",
    border: "#252D45",
    primary: "#7C5CFF",
    primaryText: "#FFFFFF",
    success: "#31C48D",
    warning: "#FACA15",
    danger: "#F05252",
    chip: "#241F45",
  },
} as const;

export type Colors = { [K in keyof (typeof Palette)["light"]]: string };

export function useColors(): Colors {
  return Palette[useColorScheme() === "light" ? "light" : "dark"];
}

export const Space = { xs: 4, sm: 8, md: 16, lg: 24, xl: 32 } as const;
export const Radius = { sm: 8, md: 14, lg: 20 } as const;
