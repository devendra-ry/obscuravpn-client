import { Card, Divider, Group, SimpleGrid, Stack, Text } from '@mantine/core';
import { useInterval } from '@mantine/hooks';
import { ReactNode, useContext, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { BsArrowDown, BsArrowUp } from 'react-icons/bs';
import { getTrafficStats, TrafficStats } from '../bridge/commands';
import { AppContext, TransportKind } from '../common/appContext';
import { TranslationKey } from '../translations/i18n';

const MODE_LABEL: Record<string, TranslationKey> = {
  [TransportKind.Quic]: 'tunnelModeStealthShort',
  [TransportKind.WireguardUdp]: 'tunnelModeSpeedShort',
  [TransportKind.TcpTls]: 'tunnelModeTcpTlsShort',
};

function fmtRate(bitsPerSecond: number): { value: string, unit: string } {
  const mbps = bitsPerSecond / 1e6;
  if (mbps >= 100) return { value: mbps.toFixed(0), unit: 'Mbit/s' };
  if (mbps >= 1) return { value: mbps.toFixed(mbps >= 10 ? 1 : 2), unit: 'Mbit/s' };
  return { value: (bitsPerSecond / 1e3).toFixed(0), unit: 'kbit/s' };
}

function fmtSession(ms: number): string {
  const totalS = Math.floor(ms / 1000);
  const h = Math.floor(totalS / 3600);
  const m = Math.floor((totalS % 3600) / 60);
  const s = totalS % 60;
  const mmss = `${String(m).padStart(h > 0 ? 2 : 1, '0')}:${String(s).padStart(2, '0')}`;
  return h > 0 ? `${h}:${mmss}` : mmss;
}

/** Download and upload rate, latency, protocol and session time of the live tunnel, as on Ombra's connect screen. */
export default function TrafficStatsCard() {
  const { t } = useTranslation();
  const { appStatus } = useContext(AppContext);
  const connected = appStatus.vpnStatus.connected;
  const [stats, setStats] = useState<TrafficStats | null>(null);
  const [rates, setRates] = useState<{ down: number, up: number } | null>(null);
  const last = useRef<{ stats: TrafficStats, at: number } | null>(null);

  const poll = async () => {
    // No service calls for a window that nobody sees (minimized or in the tray).
    if (document.hidden) return;
    try {
      const next = await getTrafficStats();
      const now = performance.now();
      const prev = last.current;
      if (prev && prev.stats.connId === next.connId && now > prev.at) {
        const seconds = (now - prev.at) / 1000;
        setRates({
          down: Math.max(0, next.rxBytes - prev.stats.rxBytes) * 8 / seconds,
          up: Math.max(0, next.txBytes - prev.stats.txBytes) * 8 / seconds,
        });
      }
      last.current = { stats: next, at: now };
      setStats(next);
    } catch {
      // The next poll tries again; a missing value shows as a dash.
    }
  };
  useInterval(() => void poll(), 1000, { autoInvoke: true });
  useEffect(() => { void poll(); }, []);

  if (!connected) return null;
  const down = rates ? fmtRate(rates.down) : null;
  const up = rates ? fmtRate(rates.up) : null;

  return (
    <Card radius='lg' padding='md' withBorder shadow='xs' w='100%' maw={460}>
      <SimpleGrid cols={2} spacing='md'>
        <Rate icon={<BsArrowDown />} label={t('statsDownload')} rate={down} />
        <Rate icon={<BsArrowUp />} label={t('statsUpload')} rate={up} />
      </SimpleGrid>
      <Divider my='sm' />
      <SimpleGrid cols={3} spacing='md'>
        <Stat label={t('statsLatency')} value={stats ? `${Math.round(stats.latestLatencyMs)} ms` : '–'} />
        <Stat label={t('statsProtocol')} value={t(MODE_LABEL[connected.transport] ?? 'tunnelModeStealthShort')} />
        <Stat label={t('statsSession')} value={stats ? fmtSession(stats.connectedMs) : '–'} />
      </SimpleGrid>
    </Card>
  );
}

function Rate({ icon, label, rate }: { icon: ReactNode, label: string, rate: { value: string, unit: string } | null }) {
  return (
    <Stack gap={0}>
      <Group gap={6} c='dimmed'>
        <Text c='orange.6' lh={1} component='span'>{icon}</Text>
        <Text size='sm' c='dimmed'>{label}</Text>
      </Group>
      <Group gap={4} align='baseline'>
        <Text fz={28} fw={600} lh={1.2}>{rate?.value ?? '–'}</Text>
        {rate && <Text size='sm' c='dimmed'>{rate.unit}</Text>}
      </Group>
    </Stack>
  );
}

function Stat({ label, value }: { label: string, value: string }) {
  return (
    <Stack gap={0}>
      <Text size='sm' c='dimmed'>{label}</Text>
      <Text size='md' fw={500}>{value}</Text>
    </Stack>
  );
}
