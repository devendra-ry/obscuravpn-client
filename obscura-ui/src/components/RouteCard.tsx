import { Card, Text, UnstyledButton } from '@mantine/core';
import { ReactNode, useContext } from 'react';
import { useTranslation } from 'react-i18next';
import { BsChevronRight, BsGlobe2, BsLaptop, BsSpeedometer2 } from 'react-icons/bs';
import { setNavigationView } from '../bridge/commands';
import { AppContext, getCityFromStatus, NavigationView } from '../common/appContext';
import { countryName, entryLabel, LocationSide, openLocationSideNext } from '../common/tunnelOptions';
import { useExitList } from '../common/useExitList';
import { useRelayList } from '../common/useRelayList';
import { showErrorNotification } from '../common/utils';
import { Flag } from './CountryFlag';
import classes from './RouteCard.module.css';

/** The country under a city, unless the city has the country's name (Singapore, Hong Kong). */
function placeSubtitle(countryCode: string, cityName: string | undefined): string | undefined {
  const country = countryName(countryCode);
  return cityName !== undefined && cityName.toLowerCase() === country.toLowerCase() ? undefined : country;
}

interface Hop {
  countryCode?: string,
  title: string,
  subtitle?: string,
}

/**
 * The tunnel as Ombra's connect screen shows it: this device, the entry relay, the exit, the internet. The relay and the
 * exit rows open the Location page on their side. The line turns green while connected.
 */
export default function RouteCard() {
  const { t } = useTranslation();
  const { appStatus, vpnConnected } = useContext(AppContext);
  const { relayList } = useRelayList({ periodS: 3600 });
  const { exitList } = useExitList({ periodS: 3600 });
  const stored = appStatus.tunnelArgs;
  if (stored === undefined) return null;
  const connected = appStatus.vpnStatus.connected;

  let entry: Hop;
  if (connected?.relay) {
    entry = { countryCode: connected.relay.country_code, title: connected.relay.city_name, subtitle: placeSubtitle(connected.relay.country_code, connected.relay.city_name) };
  } else {
    const label = entryLabel(t, stored.relay, relayList);
    entry = label.countryCode
      ? { countryCode: label.countryCode, title: label.text, subtitle: placeSubtitle(label.countryCode, label.text) }
      : { title: t('fastestEntry'), subtitle: t('fastestEntryShort') };
    if (label.countryCode && 'country' in (stored.relay ?? {})) entry.subtitle = t('anyCity');
  }

  let exit: Hop;
  if (connected) {
    const provider = connected.exit.provider_name;
    exit = {
      countryCode: connected.exit.country_code,
      title: connected.exit.city_name,
      subtitle: [placeSubtitle(connected.exit.country_code, connected.exit.city_name), provider].filter(Boolean).join(' · ') || undefined,
    };
  } else {
    const city = getCityFromStatus(appStatus.vpnStatus)
      ?? ('city' in appStatus.lastChosenExit ? appStatus.lastChosenExit.city : undefined);
    const cityName = city && exitList?.find(e => e.country_code === city.country_code && e.city_code === city.city_code)?.city_name;
    exit = city
      ? { countryCode: city.country_code, title: cityName ?? city.city_code.toUpperCase(), subtitle: placeSubtitle(city.country_code, cityName) }
      : { title: t('fastestExit'), subtitle: t('fastestExitShort') };
  }

  const open = (side: LocationSide) => {
    openLocationSideNext(side);
    setNavigationView(NavigationView.Location).catch(e => showErrorNotification(t, e, 'navigationFailed'));
  };
  const lineClass = `${classes.line} ${vpnConnected ? classes.lineConnected : ''}`;

  return (
    <Card radius='lg' padding='sm' withBorder shadow='xs' className={classes.card}>
      <EndRow icon={<BsLaptop size={20} />} label={t('routeThisDevice')} lineDown={`${lineClass} ${classes.endLineDown}`} />
      <HopRow label={t('routeEntry')} hop={entry} onClick={() => open('entry')} lineClass={lineClass} />
      <HopRow label={t('routeExit')} hop={exit} onClick={() => open('exit')} lineClass={lineClass} />
      <EndRow icon={<BsGlobe2 size={20} />} label={t('routeInternet')} lineUp={`${lineClass} ${classes.endLineUp}`} />
    </Card>
  );
}

function EndRow({ icon, label, lineUp, lineDown }: { icon: ReactNode, label: string, lineUp?: string, lineDown?: string }) {
  return (
    <div className={classes.row}>
      <div className={classes.rail}>
        {lineUp && <span className={lineUp} />}
        <span className={classes.endNode}>{icon}</span>
        {lineDown && <span className={lineDown} />}
      </div>
      <div className={classes.body} style={{ padding: '8px 0' }}>
        <Text c='dimmed'>{label}</Text>
      </div>
    </div>
  );
}

function HopRow({ label, hop, onClick, lineClass }: { label: string, hop: Hop, onClick: () => void, lineClass: string }) {
  const { t } = useTranslation();
  return (
    <UnstyledButton className={`${classes.row} ${classes.clickable}`} onClick={onClick} title={t('hopChange')}>
      <div className={classes.rail}>
        <span className={`${lineClass} ${classes.lineUp}`} />
        <span className={classes.flagNode}>{hop.countryCode ? <span className={classes.flag}><Flag countryCode={hop.countryCode} h={16} /></span> : <BsSpeedometer2 size={20} color='var(--mantine-color-orange-6)' />}</span>
        <span className={`${lineClass} ${classes.lineDown}`} />
      </div>
      <div className={classes.body}>
        <Text size='xs' c='dimmed'>{label}</Text>
        <Text size='lg' fw={500} lh={1.3}>{hop.title}</Text>
        {hop.subtitle && <Text size='sm' c='dimmed'>{hop.subtitle}</Text>}
      </div>
      <span className={classes.chevron}><BsChevronRight size={16} /></span>
    </UnstyledButton>
  );
}
