import { Platform } from "react-native";
import Purchases, { type PurchasesPackage } from "react-native-purchases";

const apiKey = Platform.select({
  ios: process.env.EXPO_PUBLIC_REVENUECAT_IOS_KEY,
  android: process.env.EXPO_PUBLIC_REVENUECAT_ANDROID_KEY,
});

let configuredFor: string | null = null;

/** Store billing only works in store-installed builds that have a RevenueCat key. */
export const purchasesAvailable = !!apiKey;

export async function initPurchases(userId: string) {
  if (!apiKey) return;
  if (configuredFor === null) {
    Purchases.configure({ apiKey, appUserID: userId });
    configuredFor = userId;
  } else if (configuredFor !== userId) {
    await Purchases.logIn(userId);
    configuredFor = userId;
  }
}

export async function getPackages(): Promise<PurchasesPackage[]> {
  if (!apiKey) return [];
  const offerings = await Purchases.getOfferings();
  return offerings.current?.availablePackages ?? [];
}

export async function buy(pkg: PurchasesPackage): Promise<boolean> {
  try {
    await Purchases.purchasePackage(pkg);
    return true;
  } catch (e) {
    if ((e as { userCancelled?: boolean }).userCancelled) return false;
    throw e;
  }
}

export async function restore() {
  if (apiKey) await Purchases.restorePurchases();
}

export async function logOutPurchases() {
  if (apiKey && configuredFor) {
    try {
      await Purchases.logOut();
    } catch {
      /* anonymous user */
    }
    configuredFor = null;
  }
}
