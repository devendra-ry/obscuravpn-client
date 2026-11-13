import { TFunction } from 'i18next';
import { Relay, RelaySelector } from '../bridge/commands';
import { PLATFORM, Platform } from '../bridge/SystemProvider';
import { getCountry } from './api';

/** Protocol, entry relay and key rotation (ported from Ombra). Only verified on Windows so far. */
export const HAS_TUNNEL_OPTIONS = PLATFORM === Platform.Windows;

/** Which hop the Location page picks. */
export type LocationSide = 'exit' | 'entry';

// The side to open the Location page on next. The Connection view sets it before it navigates there.
let pendingLocationSide: LocationSide | undefined;

export function openLocationSideNext(side: LocationSide) {
  pendingLocationSide = side;
}

/** Reads the side without clearing it, so a render stays free of side effects (StrictMode renders twice). */
export function peekPendingLocationSide(): LocationSide {
  return pendingLocationSide ?? 'exit';
}

/** Call from an effect once the Location page is mounted. */
export function clearPendingLocationSide() {
  pendingLocationSide = undefined;
}

export function countryName(countryCode: string): string {
  return getCountry(countryCode)?.name ?? countryCode.toUpperCase();
}

export function relaySelectorEquals(left: RelaySelector | undefined, right: RelaySelector | undefined): boolean {
  return JSON.stringify(left ?? { any: {} }) === JSON.stringify(right ?? { any: {} });
}

export interface EntryLabel {
  /** Lowercase country code, for the flag. Undefined for "Fastest". */
  countryCode?: string,
  text: string,
}

/** What the UI shows for an entry relay selector. */
export function entryLabel(t: TFunction, relay: RelaySelector | undefined, relays: Relay[] | undefined): EntryLabel {
  if (relay === undefined || 'any' in relay) return { text: t('relayFastest') };
  if ('country' in relay) {
    return { countryCode: relay.country.country_code, text: countryName(relay.country.country_code) };
  }
  if ('city' in relay) {
    const { country_code, city_code } = relay.city;
    const cityName = relays?.find(r => r.country_code === country_code && r.city_code === city_code)?.city_name ?? city_code.toUpperCase();
    return { countryCode: country_code, text: cityName };
  }
  const found = relays?.find(r => r.id === relay.relay.id);
  return { countryCode: found?.country_code, text: found ? `${found.city_name} (${found.id})` : relay.relay.id };
}
