#!/usr/bin/env python3
"""
Generate a standalone HTML animation for the pedestrian simulator output.

Usage:
  python3 tools/g4-visualizer/visualize_sfma.py scenarios/example out/output.csv
  python3 tools/g4-visualizer/visualize_sfma.py scenarios/example out/output.csv --out /tmp/sfma.html

The simulator output does not include an agent id. For trails, this viewer uses
the row order inside each output frame as a stable pseudo-id. That matches the
current SimulationDriver append order, but it is still a visualization aid, not
a data contract.
"""

from __future__ import annotations

import argparse
import csv
import json
from datetime import datetime
from pathlib import Path
from typing import Any


AGENT_RADIUS_METERS = 0.25


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build a local SFMA visualization HTML.")
    parser.add_argument("scenario_dir", type=Path, help="Scenario directory containing WALLS.csv, etc.")
    parser.add_argument("output_csv", type=Path, help="Simulator output CSV written by OutputSinkImpl.")
    parser.add_argument(
        "--out",
        type=Path,
        default=None,
        help="Destination HTML path. Defaults to <output_csv>.visualization.html.",
    )
    return parser.parse_args()


def read_dict_csv(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        return []
    with path.open(newline="", encoding="utf-8-sig") as fh:
        reader = csv.DictReader(fh, skipinitialspace=True)
        rows = []
        for row in reader:
            rows.append({(key or "").strip(): (value or "").strip() for key, value in row.items()})
        return rows


def as_float(row: dict[str, str], key: str, default: float = 0.0) -> float:
    value = row.get(key, "")
    if value == "":
        return default
    return float(value)


def load_segments(path: Path) -> list[dict[str, Any]]:
    segments = []
    for row in read_dict_csv(path):
        segments.append(
            {
                "x1": as_float(row, "x1"),
                "y1": as_float(row, "y1"),
                "x2": as_float(row, "x2"),
                "y2": as_float(row, "y2"),
            }
        )
    return segments


def load_blocks(path: Path) -> list[dict[str, Any]]:
    blocks = []
    for row in read_dict_csv(path):
        blocks.append(
            {
                "name": row.get("block_name", ""),
                "x1": as_float(row, "x1"),
                "y1": as_float(row, "y1"),
                "x2": as_float(row, "x2"),
                "y2": as_float(row, "y2"),
            }
        )
    return blocks


def load_targets(path: Path) -> list[dict[str, Any]]:
    targets = []
    for row in read_dict_csv(path):
        targets.append(
            {
                "name": row.get("block_name", ""),
                "type": row.get("figure_type", "").upper(),
                "radius": as_float(row, "radius"),
                "x1": as_float(row, "x1"),
                "y1": as_float(row, "y1"),
                "x2": as_float(row, "x2"),
                "y2": as_float(row, "y2"),
            }
        )
    return targets


def load_output(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        raise FileNotFoundError(f"Output file not found: {path}")

    frames_by_time: dict[float, list[dict[str, Any]]] = {}
    with path.open(newline="", encoding="utf-8-sig") as fh:
        reader = csv.reader(fh, delimiter=";")
        for raw in reader:
            row = [cell.strip() for cell in raw]
            if not row or len(row) < 6:
                continue
            if row[0].lower() in {"tout", "time", "t"}:
                continue
            try:
                t = round(float(row[0]), 4)
                x = float(row[1])
                y = float(row[2])
                vx = float(row[3])
                vy = float(row[4])
            except ValueError:
                continue

            agents = frames_by_time.setdefault(t, [])
            agents.append(
                {
                    "id": len(agents),
                    "x": x,
                    "y": y,
                    "vx": vx,
                    "vy": vy,
                    "state": row[5],
                }
            )

    return [{"time": t, "agents": frames_by_time[t]} for t in sorted(frames_by_time)]


def collect_bounds(data: dict[str, Any]) -> dict[str, float]:
    xs: list[float] = []
    ys: list[float] = []

    def add(x: float, y: float) -> None:
        xs.append(x)
        ys.append(y)

    for wall in data["walls"]:
        add(wall["x1"], wall["y1"])
        add(wall["x2"], wall["y2"])
    for group_name in ("generators", "exits", "servers"):
        for block in data[group_name]:
            add(block["x1"], block["y1"])
            add(block["x2"], block["y2"])
    for target in data["targets"]:
        if target.get("type") == "RECTANGLE":
            add(target["x1"], target["y1"])
            add(target["x2"], target["y2"])
        else:
            r = target.get("radius", 0.0)
            add(target["x1"] - r, target["y1"] - r)
            add(target["x1"] + r, target["y1"] + r)
    for frame in data["frames"]:
        for agent in frame["agents"]:
            add(agent["x"], agent["y"])

    if not xs or not ys:
        return {"minX": -1.0, "maxX": 1.0, "minY": -1.0, "maxY": 1.0}

    min_x, max_x = min(xs), max(xs)
    min_y, max_y = min(ys), max(ys)
    width = max(max_x - min_x, 1.0)
    height = max(max_y - min_y, 1.0)
    pad = max(width, height) * 0.05
    return {
        "minX": min_x - pad,
        "maxX": max_x + pad,
        "minY": min_y - pad,
        "maxY": max_y + pad,
    }


def build_data(scenario_dir: Path, output_csv: Path) -> dict[str, Any]:
    scenario_dir = scenario_dir.resolve()
    output_csv = output_csv.resolve()
    data: dict[str, Any] = {
        "scenarioName": scenario_dir.name,
        "scenarioDir": str(scenario_dir),
        "outputFile": str(output_csv),
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "agentRadius": AGENT_RADIUS_METERS,
        "walls": load_segments(scenario_dir / "WALLS.csv"),
        "generators": load_blocks(scenario_dir / "GENERATORS.csv"),
        "exits": load_blocks(scenario_dir / "EXITS.csv"),
        "servers": load_blocks(scenario_dir / "SERVERS.csv"),
        "targets": load_targets(scenario_dir / "TARGETS.csv"),
        "frames": load_output(output_csv),
    }
    data["bounds"] = collect_bounds(data)
    data["maxAgents"] = max((len(frame["agents"]) for frame in data["frames"]), default=0)
    return data


HTML_TEMPLATE = r"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>G4 SFMA Visualization</title>
  <style>
    :root {
      color-scheme: light;
      --bg: #f6f7f9;
      --panel: #ffffff;
      --text: #20242a;
      --muted: #5d6673;
      --line: #d8dde5;
      --accent: #246bfe;
      --wall: #15191f;
      --grid: #e8ecf2;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      background: var(--bg);
      color: var(--text);
      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }
    main {
      min-height: 100vh;
      display: grid;
      grid-template-columns: 320px 1fr;
    }
    aside {
      background: var(--panel);
      border-right: 1px solid var(--line);
      padding: 18px;
      display: flex;
      flex-direction: column;
      gap: 16px;
      min-width: 0;
    }
    h1 {
      margin: 0;
      font-size: 18px;
      line-height: 1.2;
    }
    .sub {
      color: var(--muted);
      font-size: 12px;
      line-height: 1.45;
      word-break: break-word;
      margin-top: 6px;
    }
    .section {
      border-top: 1px solid var(--line);
      padding-top: 14px;
    }
    .row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      margin: 9px 0;
      font-size: 13px;
    }
    .metric {
      color: var(--text);
      font-variant-numeric: tabular-nums;
      text-align: right;
    }
    button {
      height: 34px;
      border: 1px solid #b9c4d3;
      border-radius: 6px;
      background: #fff;
      color: var(--text);
      font-weight: 650;
      cursor: pointer;
    }
    button.primary {
      background: var(--accent);
      border-color: var(--accent);
      color: #fff;
    }
    button:active { transform: translateY(1px); }
    input[type="range"] {
      width: 100%;
      accent-color: var(--accent);
    }
    label.check {
      display: flex;
      align-items: center;
      gap: 8px;
      color: var(--text);
      font-size: 13px;
      margin: 8px 0;
    }
    select {
      height: 32px;
      border: 1px solid #b9c4d3;
      border-radius: 6px;
      background: #fff;
      padding: 0 8px;
    }
    #legend {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 8px 10px;
      font-size: 12px;
      color: var(--muted);
    }
    .legend-item {
      display: flex;
      align-items: center;
      gap: 6px;
      min-width: 0;
    }
    .swatch {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      flex: 0 0 auto;
    }
    #stage {
      position: relative;
      min-width: 0;
      min-height: 100vh;
    }
    canvas {
      display: block;
      width: 100%;
      height: 100vh;
      background: #fbfcfe;
    }
    #hint {
      position: absolute;
      right: 16px;
      bottom: 14px;
      padding: 8px 10px;
      background: rgba(255, 255, 255, 0.88);
      border: 1px solid var(--line);
      border-radius: 6px;
      color: var(--muted);
      font-size: 12px;
      pointer-events: none;
    }
    @media (max-width: 860px) {
      main { grid-template-columns: 1fr; }
      aside { border-right: 0; border-bottom: 1px solid var(--line); }
      canvas { height: 68vh; }
      #stage { min-height: 68vh; }
    }
  </style>
</head>
<body>
<main>
  <aside>
    <div>
      <h1>G4 SFMA Visualization</h1>
      <div class="sub" id="sourceInfo"></div>
    </div>

    <div class="section">
      <button id="playButton" class="primary">Play</button>
      <div class="row"><span>Frame</span><span class="metric" id="frameText"></span></div>
      <input id="frameSlider" type="range" min="0" max="0" value="0">
      <div class="row">
        <span>Speed</span>
        <select id="speedSelect">
          <option value="0.5">0.5x</option>
          <option value="1" selected>1x</option>
          <option value="2">2x</option>
          <option value="4">4x</option>
          <option value="8">8x</option>
        </select>
      </div>
      <label class="check"><input id="trailToggle" type="checkbox" checked> Show trails</label>
      <label class="check"><input id="velocityToggle" type="checkbox" checked> Show velocity arrows</label>
      <label class="check"><input id="labelToggle" type="checkbox"> Show geometry labels</label>
      <button id="snapshotButton">Download PNG</button>
    </div>

    <div class="section">
      <div class="row"><span>Time</span><span class="metric" id="timeText"></span></div>
      <div class="row"><span>Agents now</span><span class="metric" id="agentsText"></span></div>
      <div class="row"><span>Average speed</span><span class="metric" id="avgSpeedText"></span></div>
      <div class="row"><span>Max speed</span><span class="metric" id="maxSpeedText"></span></div>
    </div>

    <div class="section">
      <div id="legend"></div>
    </div>

    <div class="section">
      <div class="sub">
        Trails use row order as a pseudo-id because the current output format has no agent id.
      </div>
    </div>
  </aside>
  <section id="stage">
    <canvas id="canvas"></canvas>
    <div id="hint">Scroll/resize friendly canvas. Coordinates are in scenario meters.</div>
  </section>
</main>

<script>
const DATA = __DATA__;

const stateColors = {
  IDLE: "#7c8796",
  WALKING: "#1f77b4",
  APPROACHING: "#e2a400",
  ARRIVED: "#2ca02c",
  OCCUPYING: "#8a55cc",
  LEAVING: "#d62780",
  QUEUEING: "#d9483b"
};
const fallbackColor = "#39424e";

const canvas = document.getElementById("canvas");
const ctx = canvas.getContext("2d");
const slider = document.getElementById("frameSlider");
const playButton = document.getElementById("playButton");
const speedSelect = document.getElementById("speedSelect");
const trailToggle = document.getElementById("trailToggle");
const velocityToggle = document.getElementById("velocityToggle");
const labelToggle = document.getElementById("labelToggle");
const snapshotButton = document.getElementById("snapshotButton");

let frameIndex = 0;
let playing = false;
let lastTick = 0;
let accumulator = 0;

function byId(frame) {
  const map = new Map();
  for (const agent of frame.agents) map.set(agent.id, agent);
  return map;
}

const frameMaps = DATA.frames.map(byId);
const bounds = DATA.bounds;

function setup() {
  document.getElementById("sourceInfo").textContent =
    `${DATA.scenarioName} | ${DATA.frames.length} frames | ${DATA.maxAgents} max agents`;
  slider.max = Math.max(DATA.frames.length - 1, 0);
  slider.value = 0;
  buildLegend();
  resizeCanvas();
  updateFrame(0);
}

function buildLegend() {
  const legend = document.getElementById("legend");
  const states = Object.keys(stateColors);
  legend.innerHTML = states.map(state =>
    `<div class="legend-item"><span class="swatch" style="background:${stateColors[state]}"></span><span>${state}</span></div>`
  ).join("");
}

function resizeCanvas() {
  const rect = canvas.getBoundingClientRect();
  const dpr = Math.max(window.devicePixelRatio || 1, 1);
  canvas.width = Math.max(1, Math.floor(rect.width * dpr));
  canvas.height = Math.max(1, Math.floor(rect.height * dpr));
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  draw();
}

function viewport() {
  const width = canvas.clientWidth;
  const height = canvas.clientHeight;
  const pad = 34;
  const worldW = Math.max(bounds.maxX - bounds.minX, 1e-6);
  const worldH = Math.max(bounds.maxY - bounds.minY, 1e-6);
  const scale = Math.min((width - pad * 2) / worldW, (height - pad * 2) / worldH);
  const usedW = worldW * scale;
  const usedH = worldH * scale;
  const offsetX = (width - usedW) / 2;
  const offsetY = (height - usedH) / 2;
  return { width, height, scale, offsetX, offsetY };
}

function toScreen(x, y) {
  const view = viewport();
  return {
    x: view.offsetX + (x - bounds.minX) * view.scale,
    y: view.offsetY + (bounds.maxY - y) * view.scale
  };
}

function lengthToScreen(meters) {
  return meters * viewport().scale;
}

function draw() {
  const view = viewport();
  ctx.clearRect(0, 0, view.width, view.height);
  drawGrid();
  drawBlocks(DATA.generators, "rgba(36, 107, 254, 0.13)", "#246bfe", "G");
  drawBlocks(DATA.exits, "rgba(31, 142, 90, 0.16)", "#1f8e5a", "E");
  drawBlocks(DATA.servers, "rgba(232, 132, 35, 0.16)", "#d36f13", "S");
  drawTargets();
  drawWalls();
  if (trailToggle.checked) drawTrails();
  drawAgents();
}

function drawGrid() {
  const view = viewport();
  const step = niceGridStep((bounds.maxX - bounds.minX) / 8);
  ctx.save();
  ctx.strokeStyle = "#e8ecf2";
  ctx.lineWidth = 1;
  ctx.fillStyle = "#7c8796";
  ctx.font = "11px system-ui";
  for (let x = Math.ceil(bounds.minX / step) * step; x <= bounds.maxX; x += step) {
    const a = toScreen(x, bounds.minY);
    const b = toScreen(x, bounds.maxY);
    ctx.beginPath();
    ctx.moveTo(a.x, a.y);
    ctx.lineTo(b.x, b.y);
    ctx.stroke();
  }
  for (let y = Math.ceil(bounds.minY / step) * step; y <= bounds.maxY; y += step) {
    const a = toScreen(bounds.minX, y);
    const b = toScreen(bounds.maxX, y);
    ctx.beginPath();
    ctx.moveTo(a.x, a.y);
    ctx.lineTo(b.x, b.y);
    ctx.stroke();
  }
  ctx.restore();
}

function niceGridStep(raw) {
  const pow = Math.pow(10, Math.floor(Math.log10(Math.max(raw, 1e-6))));
  const normalized = raw / pow;
  if (normalized < 2) return pow;
  if (normalized < 5) return 2 * pow;
  return 5 * pow;
}

function drawWalls() {
  ctx.save();
  ctx.strokeStyle = "#15191f";
  ctx.lineWidth = 2.4;
  ctx.lineCap = "round";
  for (const wall of DATA.walls) {
    const a = toScreen(wall.x1, wall.y1);
    const b = toScreen(wall.x2, wall.y2);
    ctx.beginPath();
    ctx.moveTo(a.x, a.y);
    ctx.lineTo(b.x, b.y);
    ctx.stroke();
  }
  ctx.restore();
}

function drawBlocks(blocks, fill, stroke, prefix) {
  ctx.save();
  ctx.fillStyle = fill;
  ctx.strokeStyle = stroke;
  ctx.lineWidth = 1.5;
  for (const block of blocks) {
    const x1 = Math.min(block.x1, block.x2);
    const x2 = Math.max(block.x1, block.x2);
    const y1 = Math.min(block.y1, block.y2);
    const y2 = Math.max(block.y1, block.y2);
    const a = toScreen(x1, y1);
    const b = toScreen(x2, y2);
    if ((block.name || "").includes("_QUEUE")) {
      ctx.save();
      ctx.setLineDash([5, 4]);
      ctx.beginPath();
      ctx.moveTo(a.x, a.y);
      ctx.lineTo(b.x, b.y);
      ctx.stroke();
      ctx.setLineDash([]);
      const slots = 3;
      for (let i = 0; i < slots; i++) {
        const u = slots === 1 ? 0 : i / (slots - 1);
        const sx = a.x + (b.x - a.x) * u;
        const sy = a.y + (b.y - a.y) * u;
        ctx.beginPath();
        ctx.arc(sx, sy, 3.5, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();
      }
      ctx.restore();
      if (labelToggle.checked) drawLabel(`Q:${block.name}`, (a.x + b.x) / 2, (a.y + b.y) / 2, stroke);
      continue;
    }
    const w = Math.max(Math.abs(b.x - a.x), 3);
    const h = Math.max(Math.abs(b.y - a.y), 3);
    ctx.fillRect(a.x, b.y, w, h);
    ctx.strokeRect(a.x, b.y, w, h);
    if (labelToggle.checked) drawLabel(`${prefix}:${block.name}`, (a.x + b.x) / 2, (a.y + b.y) / 2, stroke);
  }
  ctx.restore();
}

function targetStyle(target) {
  const name = target.name || "";
  if (name.startsWith("KIOSKO")) {
    return { fill: "rgba(236, 180, 47, 0.26)", stroke: "#a26b00" };
  }
  if (name.startsWith("MESA") || name === "MESAS_ESTUDIO") {
    return { fill: "rgba(151, 111, 65, 0.22)", stroke: "#7b5730" };
  }
  if (name.startsWith("OBSTACULO")) {
    return { fill: "rgba(84, 92, 105, 0.22)", stroke: "#4f5966" };
  }
  return { fill: "rgba(106, 79, 179, 0.12)", stroke: "#6a4fb3" };
}

function drawTargets() {
  ctx.save();
  ctx.lineWidth = 1.5;
  for (const target of DATA.targets) {
    const style = targetStyle(target);
    ctx.strokeStyle = style.stroke;
    ctx.fillStyle = style.fill;
    if (target.type === "RECTANGLE") {
      const x1 = Math.min(target.x1, target.x2);
      const x2 = Math.max(target.x1, target.x2);
      const y1 = Math.min(target.y1, target.y2);
      const y2 = Math.max(target.y1, target.y2);
      const a = toScreen(x1, y1);
      const b = toScreen(x2, y2);
      const w = Math.max(Math.abs(b.x - a.x), 3);
      const h = Math.max(Math.abs(b.y - a.y), 3);
      ctx.fillRect(a.x, b.y, w, h);
      ctx.strokeRect(a.x, b.y, w, h);
      if (labelToggle.checked) drawLabel(target.name, (a.x + b.x) / 2, (a.y + b.y) / 2, style.stroke);
    } else {
      const center = toScreen(target.x1, target.y1);
      const radius = Math.max(lengthToScreen(target.radius || 0.2), 4);
      ctx.beginPath();
      ctx.arc(center.x, center.y, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
      if (labelToggle.checked) drawLabel(target.name, center.x + radius + 4, center.y, style.stroke);
    }
  }
  ctx.restore();
}

function drawTrails() {
  const start = Math.max(0, frameIndex - 40);
  ctx.save();
  ctx.lineWidth = 1.2;
  for (let i = start + 1; i <= frameIndex; i++) {
    const prev = frameMaps[i - 1];
    const cur = frameMaps[i];
    const alpha = 0.08 + 0.28 * ((i - start) / Math.max(frameIndex - start, 1));
    for (const [id, agent] of cur.entries()) {
      const old = prev.get(id);
      if (!old) continue;
      const a = toScreen(old.x, old.y);
      const b = toScreen(agent.x, agent.y);
      ctx.strokeStyle = withAlpha(stateColors[agent.state] || fallbackColor, alpha);
      ctx.beginPath();
      ctx.moveTo(a.x, a.y);
      ctx.lineTo(b.x, b.y);
      ctx.stroke();
    }
  }
  ctx.restore();
}

function drawAgents() {
  const frame = DATA.frames[frameIndex];
  const radius = Math.max(lengthToScreen(DATA.agentRadius), 3.5);
  ctx.save();
  for (const agent of frame.agents) {
    const p = toScreen(agent.x, agent.y);
    const color = stateColors[agent.state] || fallbackColor;
    ctx.beginPath();
    ctx.arc(p.x, p.y, radius, 0, Math.PI * 2);
    ctx.fillStyle = color;
    ctx.fill();
    ctx.lineWidth = 1.5;
    ctx.strokeStyle = "#ffffff";
    ctx.stroke();

    if (velocityToggle.checked) {
      const speed = Math.hypot(agent.vx, agent.vy);
      if (speed > 1e-6) drawArrow(p.x, p.y, agent.vx, agent.vy, color);
    }
  }
  ctx.restore();
}

function drawArrow(x, y, vx, vy, color) {
  const scale = Math.min(Math.max(lengthToScreen(0.55), 10), 28);
  const speed = Math.hypot(vx, vy);
  const dx = (vx / speed) * scale;
  const dy = -(vy / speed) * scale;
  const endX = x + dx;
  const endY = y + dy;
  const angle = Math.atan2(dy, dx);
  ctx.save();
  ctx.strokeStyle = color;
  ctx.fillStyle = color;
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.moveTo(x, y);
  ctx.lineTo(endX, endY);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(endX, endY);
  ctx.lineTo(endX - 6 * Math.cos(angle - 0.45), endY - 6 * Math.sin(angle - 0.45));
  ctx.lineTo(endX - 6 * Math.cos(angle + 0.45), endY - 6 * Math.sin(angle + 0.45));
  ctx.closePath();
  ctx.fill();
  ctx.restore();
}

function drawLabel(text, x, y, color) {
  ctx.save();
  ctx.font = "11px system-ui";
  ctx.fillStyle = "rgba(255, 255, 255, 0.86)";
  const width = ctx.measureText(text).width + 8;
  ctx.fillRect(x - 3, y - 12, width, 16);
  ctx.fillStyle = color;
  ctx.fillText(text, x + 1, y);
  ctx.restore();
}

function withAlpha(hex, alpha) {
  const value = hex.replace("#", "");
  const r = parseInt(value.slice(0, 2), 16);
  const g = parseInt(value.slice(2, 4), 16);
  const b = parseInt(value.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

function updateFrame(index) {
  frameIndex = Math.max(0, Math.min(index, DATA.frames.length - 1));
  slider.value = frameIndex;
  updateMetrics();
  draw();
}

function updateMetrics() {
  const frame = DATA.frames[frameIndex] || { time: 0, agents: [] };
  const speeds = frame.agents.map(a => Math.hypot(a.vx, a.vy));
  const avg = speeds.length ? speeds.reduce((a, b) => a + b, 0) / speeds.length : 0;
  const max = speeds.length ? Math.max(...speeds) : 0;
  document.getElementById("frameText").textContent = `${frameIndex + 1} / ${DATA.frames.length}`;
  document.getElementById("timeText").textContent = `${frame.time.toFixed(3)} s`;
  document.getElementById("agentsText").textContent = String(frame.agents.length);
  document.getElementById("avgSpeedText").textContent = `${avg.toFixed(3)} m/s`;
  document.getElementById("maxSpeedText").textContent = `${max.toFixed(3)} m/s`;
}

function tick(now) {
  if (!playing) return;
  if (!lastTick) lastTick = now;
  const elapsed = now - lastTick;
  lastTick = now;
  accumulator += elapsed * Number(speedSelect.value);
  const frameDelay = 90;
  while (accumulator >= frameDelay) {
    accumulator -= frameDelay;
    if (frameIndex >= DATA.frames.length - 1) {
      setPlaying(false);
      break;
    }
    updateFrame(frameIndex + 1);
  }
  requestAnimationFrame(tick);
}

function setPlaying(value) {
  playing = value;
  playButton.textContent = playing ? "Pause" : "Play";
  if (playing) {
    lastTick = 0;
    requestAnimationFrame(tick);
  }
}

playButton.addEventListener("click", () => setPlaying(!playing));
slider.addEventListener("input", () => {
  setPlaying(false);
  updateFrame(Number(slider.value));
});
trailToggle.addEventListener("change", draw);
velocityToggle.addEventListener("change", draw);
labelToggle.addEventListener("change", draw);
snapshotButton.addEventListener("click", () => {
  const link = document.createElement("a");
  link.download = `g4-sfma-${DATA.scenarioName}-t${DATA.frames[frameIndex].time.toFixed(2)}.png`;
  link.href = canvas.toDataURL("image/png");
  link.click();
});
window.addEventListener("resize", resizeCanvas);

if (DATA.frames.length === 0) {
  document.getElementById("sourceInfo").textContent = "No frames found in output file.";
} else {
  setup();
}
</script>
</body>
</html>
"""


def write_html(data: dict[str, Any], out_path: Path) -> None:
    encoded = json.dumps(data, ensure_ascii=True, separators=(",", ":"))
    html = HTML_TEMPLATE.replace("__DATA__", encoded)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(html, encoding="utf-8")


def main() -> None:
    args = parse_args()
    out_path = args.out
    if out_path is None:
        out_path = args.output_csv.with_suffix(args.output_csv.suffix + ".visualization.html")

    data = build_data(args.scenario_dir, args.output_csv)
    write_html(data, out_path.resolve())
    print(out_path.resolve())


if __name__ == "__main__":
    main()
