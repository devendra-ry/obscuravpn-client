import { makeWatchable, useSharedWatchable } from "./useSharedWatchable";
import { getRelayList, Relay, refreshRelayList } from "../bridge/commands";

const RELAY_WATCHABLE = makeWatchable(refreshRelayList, getRelayList);

export function useRelayList({ periodS }: { periodS: number }): { relayList?: Relay[], error?: Error } {
  const r = useSharedWatchable(RELAY_WATCHABLE, periodS);
  return { relayList: r.value?.value, error: r.error };
}
