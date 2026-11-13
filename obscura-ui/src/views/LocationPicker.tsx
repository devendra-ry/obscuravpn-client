import { ActionIcon, Card, Center, Collapse, Divider, Group, Loader, SegmentedControl, Stack, Text, TextInput, UnstyledButton } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { ReactNode, useContext, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { BsCheck2, BsChevronDown, BsPin, BsPinFill, BsSearch, BsSpeedometer2 } from 'react-icons/bs';
import * as commands from '../bridge/commands';
import { CONNECT_REQUIRES_ONLINE } from '../bridge/SystemProvider';
import { accountIsExpired } from '../common/api';
import { AppContext, ConnectionInProgress, NEVPNStatus } from '../common/appContext';
import { NotificationId } from '../common/notifIds';
import { clearPendingLocationSide, countryName, LocationSide, peekPendingLocationSide, relaySelectorEquals } from '../common/tunnelOptions';
import { useExitList } from '../common/useExitList';
import { useRelayList } from '../common/useRelayList';
import { normalizedIncludes } from '../common/utils';
import { Flag } from '../components/CountryFlag';
import classes from './LocationPicker.module.css';

/** One city with its servers (exits or entry relays). */
interface City {
  country_code: string,
  city_code: string,
  city_name: string,
  servers: string[],
}

interface Country {
  country_code: string,
  name: string,
  cities: City[],
  servers: number,
}

function groupByCountry(items: { id: string, country_code: string, city_code: string, city_name: string }[]): Country[] {
  const countries = new Map<string, Map<string, City>>();
  for (const item of items) {
    const cities = countries.get(item.country_code) ?? new Map<string, City>();
    countries.set(item.country_code, cities);
    const city = cities.get(item.city_code) ?? { country_code: item.country_code, city_code: item.city_code, city_name: item.city_name, servers: [] };
    cities.set(item.city_code, city);
    city.servers.push(item.id);
  }
  return [...countries.entries()]
    .map(([country_code, cities]) => {
      const list = [...cities.values()].sort((a, b) => a.city_name.localeCompare(b.city_name));
      return { country_code, name: countryName(country_code), cities: list, servers: list.reduce((n, c) => n + c.servers.length, 0) };
    })
    .sort((a, b) => a.name.localeCompare(b.name));
}

function matches(query: string, country: Country, city?: City): boolean {
  return normalizedIncludes(query, country.name) || normalizedIncludes(query, country.country_code)
    || (city !== undefined && (normalizedIncludes(query, city.city_name) || normalizedIncludes(query, city.city_code)));
}

/** The Location page of Ombra: exit or entry relay, search, the fastest choice, pinned exits, then all countries. */
export default function LocationPicker() {
  const { t } = useTranslation();
  // The side that the Connection view asked for, read once at mount.
  const [side, setSide] = useState<LocationSide>(peekPendingLocationSide);
  useEffect(clearPendingLocationSide, []);
  const [query, setQuery] = useState('');
  const q = query.trim();

  return (
    <Stack w='100%' gap='md'>
      <SegmentedControl
        fullWidth
        radius='xl'
        size='md'
        value={side}
        onChange={value => setSide(value as LocationSide)}
        data={[
          { value: 'exit', label: <SideLabel active={side === 'exit'} text={t('locationSideExit')} /> },
          { value: 'entry', label: <SideLabel active={side === 'entry'} text={t('locationSideEntry')} /> },
        ]}
      />
      <TextInput
        radius='xl'
        size='md'
        leftSection={<BsSearch />}
        placeholder={t('searchCountryOrCity')}
        value={query}
        onChange={e => setQuery(e.currentTarget.value)}
      />
      {side === 'exit' ? <ExitList query={q} /> : <EntryList query={q} />}
    </Stack>
  );
}

function SideLabel({ active, text }: { active: boolean, text: string }) {
  return <Group gap={6} justify='center' wrap='nowrap'>{active && <BsCheck2 />}<span>{text}</span></Group>;
}

/** A list that has not loaded: a spinner, or the reason once the fetch failed. An empty list is not an error. */
function ListPending({ error }: { error?: string }) {
  return error
    ? <Text c='red.7' size='sm' ml='xs' role='alert'>{error}</Text>
    : <Center py='lg'><Loader size='sm' /></Center>;
}

function SectionLabel({ children }: { children: ReactNode }) {
  return <Text c='orange.7' fw={600} size='sm' ml='xs' mt='xs'>{children}</Text>;
}

function FastestCard({ title, description, selected, disabled, onSelect }: { title: string, description: string, selected: boolean, disabled: boolean, onSelect: () => void }) {
  return (
    <Card radius='lg' withBorder shadow='xs' padding={0}>
      <UnstyledButton className={classes.row} onClick={onSelect} disabled={disabled}>
        <span className={classes.leading}><BsSpeedometer2 size={24} color='var(--mantine-color-orange-6)' /></span>
        <Stack gap={0} className={classes.text}>
          <Text size='md' fw={500}>{title}</Text>
          <Text size='sm' c='dimmed'>{description}</Text>
        </Stack>
        {selected && <span className={classes.trailing}><BsCheck2 size={20} color='var(--mantine-color-orange-6)' /></span>}
      </UnstyledButton>
    </Card>
  );
}

interface CityRowProps {
  city: City,
  indent?: boolean,
  selected: boolean,
  connected: boolean,
  disabled: boolean,
  onSelect: () => void,
  pinned?: boolean,
  onTogglePin?: () => void,
  children?: ReactNode,
}

function CityRow({ city, indent, selected, connected, disabled, onSelect, pinned, onTogglePin, children }: CityRowProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const servers = t('relayCount', { count: city.servers.length });
  return (
    <>
      {/* The pin and the chevron sit beside the row button, not inside it: a button cannot hold a button. */}
      <div className={classes.rowWrap}>
        <UnstyledButton className={`${classes.row} ${indent ? classes.indent : ''}`} onClick={onSelect} disabled={disabled}>
          <span className={classes.leading}><span className={classes.flag}><Flag countryCode={city.country_code} h={21} /></span></span>
          <Stack gap={0} className={classes.text}>
            <Text size='md' fw={500}>{city.city_name}</Text>
            <Text size='sm' c='dimmed'>
              {countryName(city.country_code)} · {servers}
              {connected && <Text span size='sm' c='green.7'> · {t('Connected')}</Text>}
            </Text>
          </Stack>
          {selected && <span className={classes.trailing}><BsCheck2 size={20} color='var(--mantine-color-orange-6)' /></span>}
        </UnstyledButton>
        {(onTogglePin || children) && (
          <Group gap={4} className={classes.actions} wrap='nowrap'>
            {onTogglePin && (
              <ActionIcon variant='subtle' color={pinned ? 'orange' : 'gray'} aria-label={pinned ? t('unpinLocation') : t('pinLocation')}
                aria-pressed={pinned} onClick={onTogglePin}>
                {pinned ? <BsPinFill /> : <BsPin />}
              </ActionIcon>
            )}
            {children && (
              <ActionIcon variant='subtle' color='gray' aria-label={t('showServers')} aria-expanded={open} onClick={() => setOpen(o => !o)}>
                <BsChevronDown className={open ? classes.chevronOpen : classes.chevron} />
              </ActionIcon>
            )}
          </Group>
        )}
      </div>
      {children && <Collapse in={open}>{children}</Collapse>}
    </>
  );
}

function CountryRow({ country, open, onToggle, children }: { country: Country, open: boolean, onToggle: () => void, children: ReactNode }) {
  const { t } = useTranslation();
  return (
    <>
      <UnstyledButton className={classes.row} onClick={onToggle}>
        <span className={classes.leading}><span className={classes.flag}><Flag countryCode={country.country_code} h={21} /></span></span>
        <Stack gap={0} className={classes.text}>
          <Text size='md' fw={500}>{country.name}</Text>
          <Text size='sm' c='dimmed'>{t('cityCount', { count: country.cities.length })} · {t('relayCount', { count: country.servers })}</Text>
        </Stack>
        <span className={classes.trailing}><BsChevronDown className={open ? classes.chevronOpen : classes.chevron} /></span>
      </UnstyledButton>
      <Collapse in={open}>{children}</Collapse>
    </>
  );
}

/** Rows in one rounded card with hairlines between them, as Ombra's grouped lists. */
function RowGroup({ children }: { children: ReactNode[] }) {
  return (
    <Card radius='lg' withBorder shadow='xs' padding={0}>
      {children.map((child, i) => <div key={i}>{i > 0 && <Divider />}{child}</div>)}
    </Card>
  );
}

function useCountriesOpen(query: string) {
  const [open, setOpen] = useState<Set<string>>(new Set());
  const isOpen = (code: string) => query !== '' || open.has(code);
  const toggle = (code: string) => setOpen(prev => {
    const next = new Set(prev);
    if (next.has(code)) next.delete(code); else next.add(code);
    return next;
  });
  return { isOpen, toggle };
}

function ExitList({ query }: { query: string }) {
  const { t } = useTranslation();
  const { appStatus, osStatus, showOfflineUI, accountInfo, vpnConnect, vpnConnected, connectionInProgress } = useContext(AppContext);
  const { exitList, error } = useExitList({ periodS: 60 });
  const { isOpen, toggle } = useCountriesOpen(query);
  if (exitList === undefined) return <ListPending error={error && t('failedToFetchExitServers')} />;
  const countries = groupByCountry(exitList);
  const connectedExit = appStatus.vpnStatus.connected?.exit;
  // The stored exit, also "Fastest". `lastChosenExit` never holds "Fastest" once a city was chosen.
  const chosen = appStatus.tunnelArgs?.exit ?? appStatus.lastChosenExit;
  const fastestChosen = 'any' in chosen;
  const isAccountExpired = accountInfo ? accountIsExpired(accountInfo) : false;
  const disabled = osStatus.osVpnStatus === NEVPNStatus.Disconnecting || (showOfflineUI && CONNECT_REQUIRES_ONLINE) || isAccountExpired;

  const isConnectedCity = (c: City) => connectedExit?.country_code === c.country_code && connectedExit.city_code === c.city_code;
  // A country or server choice (from the tray) has no row here, so the connected city stands in for it.
  const isChosenCity = (c: City) => 'city' in chosen
    ? chosen.city.country_code === c.country_code && chosen.city.city_code === c.city_code
    : !fastestChosen && isConnectedCity(c);
  const pinned = (c: City) => appStatus.pinnedLocations.some(p => p.country_code === c.country_code && p.city_code === c.city_code);

  const choose = (exit: commands.ExitSelector, name: string) => {
    if (vpnConnected || connectionInProgress !== ConnectionInProgress.UNSET) {
      notifications.hide(NotificationId.VPN_DISCONNECT_CONNECT);
      notifications.show({ title: t('connectingToCity', { city: name }), message: '', autoClose: 15_000, color: 'yellow.4', id: NotificationId.VPN_DISCONNECT_CONNECT });
    }
    void vpnConnect(exit);
  };
  const chooseCity = (c: City) => {
    // The connected city can still be chosen when "Fastest" picked it: that pins the city.
    if (!(isConnectedCity(c) && isChosenCity(c))) choose({ city: { country_code: c.country_code, city_code: c.city_code } }, c.city_name);
  };
  const togglePin = (c: City) => {
    const rest = appStatus.pinnedLocations.filter(p => !(p.country_code === c.country_code && p.city_code === c.city_code));
    void commands.setPinnedExits(rest.length !== appStatus.pinnedLocations.length
      ? rest
      : [...appStatus.pinnedLocations, { country_code: c.country_code, city_code: c.city_code, pinned_at: Math.floor(Date.now() / 1000) }]);
  };
  const cityRow = (c: City, indent = false) => (
    <CityRow key={`${c.country_code}-${c.city_code}`} city={c} indent={indent} selected={isChosenCity(c)} connected={isConnectedCity(c)}
      disabled={disabled} onSelect={() => chooseCity(c)} pinned={pinned(c)} onTogglePin={() => togglePin(c)} />
  );

  const pinnedCities = countries.flatMap(country => country.cities.filter(c => pinned(c) && (!query || matches(query, country, c))));
  const shown = countries.filter(country => !query || matches(query, country) || country.cities.some(c => matches(query, country, c)));

  return (
    <>
      {!query && (
        <FastestCard title={t('fastestExit')} description={t('fastestExitDescription')} disabled={disabled}
          selected={fastestChosen} onSelect={() => { if (!(fastestChosen && connectedExit)) choose({ any: {} }, t('fastestExit')); }} />
      )}
      {pinnedCities.length > 0 && (
        <>
          <SectionLabel>{t('Pinned')}</SectionLabel>
          <RowGroup>{pinnedCities.map(c => cityRow(c))}</RowGroup>
        </>
      )}
      <SectionLabel>{t('allLocations')}</SectionLabel>
      {shown.length === 0 ? <Text c='dimmed' size='sm' ml='xs'>{t('noLocationsFound')}</Text> : (
        <RowGroup>
          {shown.map(country => (
            <CountryRow key={country.country_code} country={country} open={isOpen(country.country_code)} onToggle={() => toggle(country.country_code)}>
              {country.cities.filter(c => !query || matches(query, country) || matches(query, country, c)).map(c => <div key={c.city_code}><Divider />{cityRow(c, true)}</div>)}
            </CountryRow>
          ))}
        </RowGroup>
      )}
    </>
  );
}

function EntryList({ query }: { query: string }) {
  const { t } = useTranslation();
  const { appStatus, osStatus } = useContext(AppContext);
  const { relayList, error } = useRelayList({ periodS: 60 });
  const { showLoadingUI, execute: setTunnelArgs } = commands.useCommand({ command: commands.setTunnelArgs, showNotification: true });
  const { isOpen, toggle } = useCountriesOpen(query);
  const stored = appStatus.tunnelArgs;
  if (stored === undefined) return null;
  if (relayList === undefined) return <ListPending error={error && t('failedToFetchEntryRelays')} />;
  const countries = groupByCountry(relayList);
  const connectedRelay = appStatus.vpnStatus.connected?.relay;
  const disabled = showLoadingUI || osStatus.osVpnStatus === NEVPNStatus.Disconnecting;
  const choose = (relay: commands.RelaySelector) => {
    if (!relaySelectorEquals(relay, stored.relay)) void setTunnelArgs({ ...stored, relay });
  };
  const shown = countries.filter(country => !query || matches(query, country) || country.cities.some(c => matches(query, country, c)));

  return (
    <>
      {!query && (
        <FastestCard title={t('fastestEntry')} description={t('fastestEntryDescription')} disabled={disabled}
          selected={relaySelectorEquals(stored.relay, { any: {} })} onSelect={() => choose({ any: {} })} />
      )}
      <SectionLabel>{t('allLocations')}</SectionLabel>
      {shown.length === 0 ? <Text c='dimmed' size='sm' ml='xs'>{t('noLocationsFound')}</Text> : (
        <RowGroup>
          {shown.map(country => {
            const anyInCountry = { country: { country_code: country.country_code } };
            return (
              <CountryRow key={country.country_code} country={country} open={isOpen(country.country_code)} onToggle={() => toggle(country.country_code)}>
                {country.cities.length > 1 && (
                  <div><Divider />
                    <UnstyledButton className={`${classes.row} ${classes.indent}`} onClick={() => choose(anyInCountry)} disabled={disabled}>
                      <span className={classes.leading}><span className={classes.flag}><Flag countryCode={country.country_code} h={21} /></span></span>
                      <Stack gap={0} className={classes.text}>
                        <Text size='md' fw={500}>{t('relayAnyIn', { location: country.name })}</Text>
                        <Text size='sm' c='dimmed'>{t('relayCount', { count: country.servers })}</Text>
                      </Stack>
                      {relaySelectorEquals(stored.relay, anyInCountry) && <span className={classes.trailing}><BsCheck2 size={20} color='var(--mantine-color-orange-6)' /></span>}
                    </UnstyledButton>
                  </div>
                )}
                {country.cities.filter(c => !query || matches(query, country) || matches(query, country, c)).map(c => {
                  const city = { city: { country_code: c.country_code, city_code: c.city_code } };
                  const connectedHere = connectedRelay?.country_code === c.country_code && connectedRelay.city_code === c.city_code;
                  return (
                    <div key={c.city_code}><Divider />
                      <CityRow city={c} indent selected={relaySelectorEquals(stored.relay, city)} connected={connectedHere}
                        disabled={disabled} onSelect={() => choose(city)}>
                        {c.servers.length > 1 ? c.servers.map(id => (
                          <div key={id}><Divider />
                            <UnstyledButton className={`${classes.row} ${classes.indent2}`} onClick={() => choose({ relay: { id } })} disabled={disabled}>
                              <Stack gap={0} className={classes.text}>
                                <Text size='sm' fw={500} ff='monospace'>{id}</Text>
                                {connectedRelay?.id === id && <Text size='xs' c='green.7'>{t('Connected')}</Text>}
                              </Stack>
                              {relaySelectorEquals(stored.relay, { relay: { id } }) && <span className={classes.trailing}><BsCheck2 size={20} color='var(--mantine-color-orange-6)' /></span>}
                            </UnstyledButton>
                          </div>
                        )) : undefined}
                      </CityRow>
                    </div>
                  );
                })}
              </CountryRow>
            );
          })}
        </RowGroup>
      )}
    </>
  );
}
