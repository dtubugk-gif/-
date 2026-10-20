import * as Contacts from "expo-contacts";
import { parsePhoneNumberFromString, type CountryCode } from "libphonenumber-js";

let index: Map<string, string> | null = null;

/** On-device only: builds an E.164 -> contact name map. Contacts never leave the phone. */
async function build(country: string): Promise<Map<string, string>> {
  const map = new Map<string, string>();
  const { data } = await Contacts.getContactsAsync({ fields: [Contacts.Fields.PhoneNumbers, Contacts.Fields.Name] });
  for (const c of data) {
    for (const p of c.phoneNumbers ?? []) {
      const parsed = parsePhoneNumberFromString(p.number ?? p.digits ?? "", country as CountryCode);
      if (parsed?.isValid() && c.name) map.set(parsed.number, c.name);
    }
  }
  return map;
}

export async function contactName(e164: string, country: string, ask = false): Promise<string | null> {
  let { status } = await Contacts.getPermissionsAsync();
  if (status !== "granted" && ask) status = (await Contacts.requestPermissionsAsync()).status;
  if (status !== "granted") return null;
  index ??= await build(country);
  return index.get(e164) ?? null;
}
