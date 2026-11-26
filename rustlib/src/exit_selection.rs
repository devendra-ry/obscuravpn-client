use std::{
    collections::{HashMap, HashSet},
    num::Saturating,
    time::{Duration, Instant},
};

use obscuravpn_api::types::{CityCode, CountryCode, OneExit, OneRelay, RelayPreferredExit};
use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq, Clone)]
#[serde(rename_all = "camelCase")]
pub enum ExitSelector {
    Any {},
    Exit {
        id: String,
    },
    Country {
        country_code: CountryCode,
    },
    City {
        #[serde(flatten)]
        city_code: CityCode,
    },
}

impl ExitSelector {
    pub fn matches(&self, candidate: &OneExit) -> bool {
        match self {
            ExitSelector::Any {} => true,
            ExitSelector::Exit { id } => candidate.id == *id,
            ExitSelector::Country { country_code } => candidate.city_code.country_code == *country_code,
            ExitSelector::City { city_code } => candidate.city_code == *city_code,
        }
    }
}

impl Default for ExitSelector {
    fn default() -> Self {
        ExitSelector::Any {}
    }
}

/// How long an exit whose tunnel failed is passed over. Exits that complete the WireGuard handshake and then answer
/// nothing stay that way for minutes, and a retry of the same exit costs about 6 s.
const FAILED_EXIT_AVOID_TIME: Duration = Duration::from_secs(5 * 60);

/// The exit picks of one connect series (spread over exits, datacenters, cities and countries), plus the exits that
/// failed recently, which outlive the series.
#[derive(Debug, Default)]
pub struct ExitSelectionState {
    round: SelectionRound,
    failed_exits: HashMap<String, Instant>,
    last_selected: Option<String>,
}

#[derive(Debug, Default)]
struct SelectionRound {
    selected_exit_ids: HashSet<String>,
    selected_datacenters: HashMap<u32, Saturating<u8>>,
    selected_cities: HashMap<CityCode, Saturating<u8>>,
    selected_countries: HashMap<CountryCode, Saturating<u8>>,
}

impl ExitSelectionState {
    /// Starts over with the spread, after a connect or a change of the target. Recent failures are kept.
    pub fn new_round(&mut self) {
        self.round = SelectionRound::default();
    }

    /// The tunnel to the exit picked last failed to connect or broke soon after.
    pub fn last_selected_failed(&mut self) {
        if let Some(id) = self.last_selected.take() {
            self.exit_failed(id);
        }
    }

    pub fn exit_failed(&mut self, id: String) {
        tracing::info!(message_id = "Lq5vXn8D", exit.id = id, "passing over this exit for a while");
        self.failed_exits.insert(id, Instant::now());
    }

    pub fn select_next_exit<'a>(&mut self, selector: &ExitSelector, exits: &'a [OneExit], relay: &OneRelay) -> Option<&'a OneExit> {
        self.failed_exits.retain(|_, failed_at| failed_at.elapsed() < FAILED_EXIT_AVOID_TIME);
        let matching = || exits.iter().filter(|candidate| selector.matches(candidate));
        let pick = |round: &SelectionRound, skip_failed: bool| {
            matching()
                .filter(|candidate| !round.exclude(candidate))
                .filter(|candidate| !(skip_failed && self.failed_exits.contains_key(&candidate.id)))
                .max_by_key(|candidate| Self::rank(candidate, &relay.city_code, &relay.preferred_exits))
        };
        let mut selected = pick(&self.round, true);
        if selected.is_none() {
            // Every matching exit was tried or failed recently: start over at once instead of failing this connect.
            tracing::warn!(message_id = "PrOiO8XS", "no exits left to select, clearing adaptive filters");
            self.round = SelectionRound::default();
            selected = pick(&self.round, true).or_else(|| pick(&self.round, false));
        }

        if let Some(selected) = selected {
            self.round.add(selected);
        }
        self.last_selected = selected.map(|exit| exit.id.clone());
        selected
    }

    fn rank(candidate: &OneExit, relay_city_code: &CityCode, relay_preferred_exits: &[RelayPreferredExit]) -> (bool, bool, bool, u8, u32) {
        let is_preferred = relay_preferred_exits.iter().any(|e| e.id == candidate.id);
        let same_country = relay_city_code.country_code == candidate.city_code.country_code;
        let same_city = relay_city_code == &candidate.city_code;
        (is_preferred, same_city, same_country, candidate.tier, rand::random())
    }
}

impl SelectionRound {
    fn add(&mut self, selected: &OneExit) {
        self.selected_exit_ids.insert(selected.id.clone());
        *self.selected_datacenters.entry(selected.datacenter_id).or_insert(Saturating(0)) += 1;
        *self.selected_cities.entry(selected.city_code.clone()).or_insert(Saturating(0)) += 1;
        *self
            .selected_countries
            .entry(selected.city_code.country_code.clone())
            .or_insert(Saturating(0)) += 1;
    }

    fn exclude(&self, candidate: &OneExit) -> bool {
        self.selected_exit_ids.contains(&candidate.id)
            || self.selected_datacenters.get(&candidate.datacenter_id) >= Some(&Saturating(2))
            || self.selected_cities.get(&candidate.city_code) >= Some(&Saturating(4))
            || self.selected_countries.get(&candidate.city_code.country_code) >= Some(&Saturating(6))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn city(cc: &str, city: &str) -> CityCode {
        CityCode { country_code: CountryCode(cc.into()), city_code: city.into() }
    }

    fn exit(id: &str, datacenter_id: u32) -> OneExit {
        OneExit {
            id: id.into(),
            city_code: city("jp", "tyo"),
            city_name: "Tokyo".into(),
            datacenter_id,
            provider_id: id.into(),
            provider_url: String::new(),
            provider_name: "Mullvad VPN".into(),
            provider_homepage_url: String::new(),
            tier: 0,
        }
    }

    fn relay() -> OneRelay {
        OneRelay {
            id: "SIN-001".into(),
            city_code: city("sg", "sin"),
            city_name: "Singapore".into(),
            preferred_exits: Vec::new(),
            ip_v4: std::net::Ipv4Addr::LOCALHOST,
            ports: vec![443],
            tls_cert: Vec::new(),
        }
    }

    fn tokyo() -> ExitSelector {
        ExitSelector::City { city_code: city("jp", "tyo") }
    }

    fn pick(state: &mut ExitSelectionState, exits: &[OneExit]) -> String {
        state.select_next_exit(&tokyo(), exits, &relay()).unwrap().id.clone()
    }

    #[test]
    fn an_exit_that_failed_is_passed_over_also_in_the_next_round() {
        let exits = [exit("tyo-1", 1), exit("tyo-2", 2)];
        let mut state = ExitSelectionState::default();
        let first = pick(&mut state, &exits);
        // The tunnel connected, then broke soon after: a new round, as after a connect.
        state.new_round();
        state.exit_failed(first.clone());
        for _ in 0..5 {
            state.new_round();
            assert_ne!(pick(&mut state, &exits), first);
        }
    }

    #[test]
    fn a_failed_connect_marks_the_exit_picked_last() {
        let exits = [exit("tyo-1", 1), exit("tyo-2", 2)];
        let mut state = ExitSelectionState::default();
        let first = pick(&mut state, &exits);
        state.last_selected_failed();
        state.new_round();
        assert_ne!(pick(&mut state, &exits), first);
    }

    #[test]
    fn when_every_exit_failed_one_is_still_picked() {
        let exits = [exit("tyo-1", 1)];
        let mut state = ExitSelectionState::default();
        state.exit_failed("tyo-1".into());
        assert_eq!(pick(&mut state, &exits), "tyo-1");
        assert_eq!(pick(&mut state, &exits), "tyo-1", "a tried exit is picked again instead of no exit");
    }

    #[test]
    fn running_out_of_exits_starts_a_new_round_at_once() {
        // Two exits in one datacenter: upstream gave no exit (a failed connect) on the third pick.
        let exits = [exit("tyo-1", 1), exit("tyo-2", 1)];
        let mut state = ExitSelectionState::default();
        for _ in 0..6 {
            assert!(state.select_next_exit(&tokyo(), &exits, &relay()).is_some());
        }
    }
}
