#!/usr/bin/env python3
"""TCP and UDP relays for the isolated two-client aircraft audit; no packet rewriting."""
import argparse
import asyncio
import json
import random
import signal
import time
from pathlib import Path

async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    started = time.monotonic()
    stats = {'ports': [], 'upstream': '127.0.0.1:25585', 'delay_phases_ms': [25, 75, 125, 40],
             'jitter_ms': 5, 'udp_datagrams': 0, 'connections': 0, 'chunks': 0, 'bytes': 0, 'min_delay_ms': None, 'max_delay_ms': 0,
             'delivered_tcp_chunks': 0, 'max_observed_tcp_delay_ms': 0, 'max_tcp_schedule_overrun_ms': 0}
    stop = asyncio.Event()
    rng = random.Random(2101)
    def save():
        temporary = args.output.with_suffix('.tmp')
        temporary.write_text(json.dumps(stats, indent=2) + '\n')
        temporary.replace(args.output)
    async def relay(reader, writer):
        queue = asyncio.Queue()
        async def receive():
            previous = 0
            try:
                while data := await reader.read(65536):
                    now = time.monotonic()
                    delay = ([25, 75, 125, 40][int((now-started)/5) % 4] + rng.uniform(-5, 5))/1000
                    due = max(previous, now+delay)
                    previous = due
                    stats['chunks'] += 1
                    stats['bytes'] += len(data)
                    actual = (due-now)*1000
                    stats['min_delay_ms'] = min(stats['min_delay_ms'] or actual, actual)
                    stats['max_delay_ms'] = max(stats['max_delay_ms'], actual)
                    await queue.put((due, now, data))
            finally:
                await queue.put((0, 0, None))
        task = asyncio.create_task(receive())
        try:
            while True:
                due, received, data = await queue.get()
                if data is None: break
                await asyncio.sleep(max(0, due-time.monotonic()))
                delivered = time.monotonic()
                stats['delivered_tcp_chunks'] += 1
                stats['max_observed_tcp_delay_ms'] = max(stats['max_observed_tcp_delay_ms'], (delivered-received)*1000)
                stats['max_tcp_schedule_overrun_ms'] = max(stats['max_tcp_schedule_overrun_ms'], (delivered-due)*1000)
                writer.write(data)
                await writer.drain()
        finally:
            task.cancel()
            writer.close()
    async def connect(reader, writer):
        stats['connections'] += 1
        try:
            upstream_reader, upstream_writer = await asyncio.open_connection('127.0.0.1', 25585)
            await asyncio.gather(relay(reader, upstream_writer), relay(upstream_reader, writer))
        except (ConnectionError, asyncio.CancelledError):
            pass
        finally:
            writer.close()
            save()
    async def delayed_datagram(transport, data, address=None):
        now=time.monotonic()
        delay=([25,75,125,40][int((now-started)/5)%4]+rng.uniform(-5,5))/1000
        await asyncio.sleep(delay)
        if not transport.is_closing(): transport.sendto(data,address)
        stats['udp_datagrams'] += 1

    class UpstreamUdp(asyncio.DatagramProtocol):
        def __init__(self, frontend, client): self.frontend, self.client = frontend, client
        def datagram_received(self, data, address):
            asyncio.create_task(delayed_datagram(self.frontend.transport,data,self.client))
        def error_received(self, error): pass

    class FrontendUdp(asyncio.DatagramProtocol):
        def __init__(self): self.peers={}
        def connection_made(self, transport): self.transport=transport
        async def peer(self, address):
            transport,_=await asyncio.get_running_loop().create_datagram_endpoint(
                lambda:UpstreamUdp(self,address),remote_addr=('127.0.0.1',25585))
            return transport
        async def forward(self,data,address):
            if address not in self.peers: self.peers[address]=asyncio.create_task(self.peer(address))
            transport=await self.peers[address]
            await delayed_datagram(transport,data)
        def datagram_received(self,data,address): asyncio.create_task(self.forward(data,address))

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM): loop.add_signal_handler(sig, stop.set)
    servers = [await asyncio.start_server(connect, '127.0.0.1', port) for port in (0,0)]
    stats['ports'] = [server.sockets[0].getsockname()[1] for server in servers]
    udp=[await loop.create_datagram_endpoint(FrontendUdp,local_addr=('127.0.0.1',port)) for port in stats['ports']]
    save()
    while not stop.is_set():
        try: await asyncio.wait_for(stop.wait(), timeout=5)
        except asyncio.TimeoutError: pass
        save()
    for transport,protocol in udp: transport.close()
    for server in servers: server.close(); await server.wait_closed()
    save()

if __name__ == '__main__': asyncio.run(main())
