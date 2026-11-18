                                    <  *** T-WAVE BLUETOOTH SPEAKER™ ***   />
                                      
<img width="2084" height="1692" alt="IMG_20261118_125541" src="https://github.com/user-attachments/assets/11c7ac71-213d-4a27-9b5a-401950da4235" />


   Screenshots
   
<img width="3200" height="3356" alt="Screenshot_20261118-131824_t_wave" src="https://github.com/user-attachments/assets/573fed5f-179f-44d3-9480-c83159ce8578" />
<img width="3200" height="2360" alt="Screenshot_20261118-132833_t_wave" src="https://github.com/user-attachments/assets/b9a2de0b-016b-4294-9232-64577ee556ef" />
[t_wave_technical_architecture_how_it_works (1).html](https://github.com/user-attachments/files/35016245/t_wave_technical_architecture_how_it_works.1.html)
<!DOCTYPE html>
<html lang="en" class="dark scroll-smooth">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>t_wave — How It Works | Android High-Res Bluetooth Audio Receiver</title>
  <script src="https://cdn.tailwindcss.com"></script>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600;700&display=swap" rel="stylesheet">
  <script>
    tailwind.config = {
      darkMode: 'class',
      theme: {
        extend: {
          fontFamily: {
            sans: ['Inter', 'sans-serif'],
            mono: ['JetBrains Mono', 'monospace'],
          },
          colors: {
            obsidian: '#07090e',
            panel: '#0d111a',
            panelBorder: '#1b2333',
            cyanGlow: '#00f0ff',
            violetGlow: '#9d4edd',
            emeraldGlow: '#10b981',
            amberGlow: '#f59e0b'
          }
        }
      }
    }
  </script>
  <style>
    body {
      background-color: #07090e;
      color: #e2e8f0;
      overflow-x: hidden;
    }
    /* Custom subtle grid background */
    .bg-grid {
      background-size: 36px 36px;
      background-image: 
        linear-gradient(to right, rgba(255, 255, 255, 0.025) 1px, transparent 1px),
        linear-gradient(to bottom, rgba(255, 255, 255, 0.025) 1px, transparent 1px);
    }
    /* Custom range slider */
    input[type=range] {
      -webkit-appearance: none;
      background: transparent;
    }
    input[type=range]::-webkit-slider-runnable-track {
      height: 6px;
      border-radius: 999px;
      background: #1e293b;
    }
    input[type=range]::-webkit-slider-thumb {
      -webkit-appearance: none;
      height: 18px;
      width: 18px;
      border-radius: 50%;
      background: #00f0ff;
      cursor: pointer;
      margin-top: -6px;
      box-shadow: 0 0 12px rgba(0, 240, 255, 0.7);
      transition: transform 0.15s ease;
    }
    input[type=range]::-webkit-slider-thumb:hover {
      transform: scale(1.15);
    }
    .glass-card {
      background: rgba(13, 17, 26, 0.85);
      backdrop-filter: blur(12px);
      border: 1px solid rgba(30, 41, 59, 0.8);
    }
    .code-pill {
      font-family: 'JetBrains Mono', monospace;
      font-size: 0.78rem;
      background: rgba(0, 240, 255, 0.08);
      color: #67e8f9;
      border: 1px solid rgba(0, 240, 255, 0.2);
      padding: 0.1rem 0.42rem;
      border-radius: 0.375rem;
      word-break: break-word;
    }
    .code-pill-violet {
      font-family: 'JetBrains Mono', monospace;
      font-size: 0.78rem;
      background: rgba(157, 78, 221, 0.12);
      color: #d8b4fe;
      border: 1px solid rgba(157, 78, 221, 0.28);
      padding: 0.1rem 0.42rem;
      border-radius: 0.375rem;
      word-break: break-word;
    }
    .code-pill-emerald {
      font-family: 'JetBrains Mono', monospace;
      font-size: 0.78rem;
      background: rgba(16, 185, 129, 0.1);
      color: #6ee7b7;
      border: 1px solid rgba(16, 185, 129, 0.25);
      padding: 0.1rem 0.42rem;
      border-radius: 0.375rem;
      word-break: break-word;
    }
    /* Custom scrollbar */
    ::-webkit-scrollbar {
      width: 8px;
      height: 8px;
    }
    ::-webkit-scrollbar-track {
      background: #07090e;
    }
    ::-webkit-scrollbar-thumb {
      background: #1e293b;
      border-radius: 4px;
    }
    ::-webkit-scrollbar-thumb:hover {
      background: #334155;
    }
  </style>
</head>
<body class="bg-grid font-sans antialiased selection:bg-cyan-500/30 selection:text-cyan-200">

  <!-- Sticky Top Navigation -->
  <header class="sticky top-0 z-50 border-b border-slate-800/80 bg-obsidian/85 backdrop-blur-md">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between gap-4">
      <a href="#" class="flex items-center gap-3 group">
        <div class="w-9 h-9 rounded-xl bg-gradient-to-br from-cyan-400 to-violet-600 p-[1px] shadow-lg shadow-cyan-500/20">
          <div class="w-full h-full bg-obsidian rounded-[11px] flex items-center justify-center">
            <svg class="w-5 h-5 text-cyan-400 group-hover:scale-110 transition-transform" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M3 12h3l3-7 4 14 3-7h5" />
            </svg>
          </div>
        </div>
        <div>
          <span class="font-mono font-bold text-lg tracking-tight text-white">t_wave</span>
          <span class="hidden sm:inline-block ml-2 text-[11px] font-mono uppercase tracking-wider px-2 py-0.5 rounded-full bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">Architecture</span>
        </div>
      </a>

      <nav class="hidden md:flex items-center gap-6 text-sm font-medium text-slate-400">
        <a href="#dual-receiver" class="hover:text-cyan-400 transition-colors">1. Dual Receiver</a>
        <a href="#audio-engine" class="hover:text-cyan-400 transition-colors">2. AudioTrack & Sync</a>
        <a href="#lock-screen" class="hover:text-cyan-400 transition-colors">3. Lock Screen</a>
        <a href="#signal-scope" class="hover:text-cyan-400 transition-colors">4. Signal Scope</a>
        <a href="#state-persistence" class="hover:text-cyan-400 transition-colors">5. State & DataStore</a>
        <a href="#about" class="hover:text-cyan-400 transition-colors">About</a>
      </nav>

      <div class="flex items-center gap-2">
        <a href="#signal-scope" class="px-3.5 py-2 rounded-xl bg-cyan-500/15 hover:bg-cyan-500/25 text-cyan-300 border border-cyan-500/30 font-mono text-xs font-semibold transition-all flex items-center gap-2">
          <span class="w-2 h-2 rounded-full bg-cyan-400 animate-pulse"></span>
          <span>Live Scope</span>
        </a>
      </div>
    </div>
  </header>

  <!-- Hero Section -->
  <section class="relative pt-12 pb-16 sm:pt-16 sm:pb-20 overflow-hidden border-b border-slate-800/70">
    <!-- Ambient Glows -->
    <div class="pointer-events-none absolute -top-24 left-1/4 w-96 h-96 bg-cyan-500/10 rounded-full blur-3xl"></div>
    <div class="pointer-events-none absolute top-10 right-1/4 w-96 h-96 bg-violet-600/10 rounded-full blur-3xl"></div>

    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative">
      <!-- Live Mini-Visualizer Banner -->
      <div class="mb-8 rounded-2xl glass-card p-3 sm:p-4 border border-slate-800/90 shadow-2xl">
        <div class="flex flex-wrap items-center justify-between gap-2 mb-2 px-1">
          <div class="flex items-center gap-2">
            <span class="inline-flex h-2.5 w-2.5 rounded-full bg-emerald-400 animate-ping"></span>
            <span class="font-mono text-xs uppercase tracking-widest text-slate-300">PCM Telemetry Stream // Active Preview</span>
          </div>
          <div class="flex items-center gap-3 font-mono text-[11px] text-slate-400">
            <span>UUID: <strong class="text-cyan-400">0x110B</strong> / <strong class="text-violet-400">SPP</strong></span>
            <span class="hidden sm:inline">|</span>
            <span class="hidden sm:inline">MAX: <strong class="text-emerald-400">192kHz / 32-bit</strong></span>
          </div>
        </div>
        <div class="w-full h-16 sm:h-20 rounded-xl bg-slate-950/90 border border-slate-800/80 overflow-hidden relative">
          <canvas id="heroMiniCanvas" class="w-full h-full block"></canvas>
        </div>
      </div>

      <div class="max-w-4xl">
        <div class="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-violet-500/10 border border-violet-500/30 text-violet-300 font-mono text-xs font-semibold uppercase tracking-wider mb-5">
          <svg class="w-3.5 h-3.5 text-violet-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
          </svg>
          HOW DOES IT WORK? — UNDER THE HOOD
        </div>

        <h1 class="text-4xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight text-white leading-[1.1] mb-5">
          Turn Your Android Device Into a <span class="text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 via-sky-300 to-violet-400">High-Res Bluetooth Audio Receiver</span>
        </h1>

        <p class="text-base sm:text-lg text-slate-400 leading-relaxed max-w-3xl mb-8">
          Explore how <strong class="text-white font-mono">t_wave</strong> turns an Android phone or tablet into a wireless loudspeaker across its dual Bluetooth receiver modes, HAL-aligned high-res <span class="code-pill">AudioTrack</span> pipeline, millisecond sync offset engine, lock-screen console, and real-time <span class="code-pill">Signal Scope</span> visualizer.
        </p>

        <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 sm:gap-4">
          <div class="rounded-2xl glass-card p-4">
            <div class="font-mono text-xs text-slate-400 mb-1">RECEIVER MODES</div>
            <div class="font-mono text-lg sm:text-xl font-bold text-cyan-400">A2DP + SPP</div>
            <div class="text-xs text-slate-500 mt-0.5">Root &amp; Non-Root</div>
          </div>
          <div class="rounded-2xl glass-card p-4">
            <div class="font-mono text-xs text-slate-400 mb-1">MAX RESOLUTION</div>
            <div class="font-mono text-lg sm:text-xl font-bold text-emerald-400">192kHz/32b</div>
            <div class="text-xs text-slate-500 mt-0.5">Raw Stereo PCM</div>
          </div>
          <div class="rounded-2xl glass-card p-4">
            <div class="font-mono text-xs text-slate-400 mb-1">SYNC OFFSET</div>
            <div class="font-mono text-lg sm:text-xl font-bold text-violet-400">-250..+500ms</div>
            <div class="text-xs text-slate-500 mt-0.5">5 ms Circular Step</div>
          </div>
          <div class="rounded-2xl glass-card p-4">
            <div class="font-mono text-xs text-slate-400 mb-1">SIGNAL SCOPE</div>
            <div class="font-mono text-lg sm:text-xl font-bold text-amber-400">8 Engines</div>
            <div class="text-xs text-slate-500 mt-0.5">28-Band FFT / RMS</div>
          </div>
        </div>
      </div>
    </div>
  </section>

  <!-- Main Technical Content -->
  <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-14 space-y-24">

    <!-- SECTION 1: Dual Bluetooth Receiver Architecture -->
    <section id="dual-receiver" class="scroll-mt-24">
      <div class="flex flex-col md:flex-row md:items-end justify-between gap-4 mb-8">
        <div>
          <span class="font-mono text-xs font-semibold uppercase tracking-widest text-cyan-400">01 // BLUETOOTH TRANSPORT LAYER</span>
          <h2 class="text-2xl sm:text-3xl font-bold text-white mt-1">Dual Bluetooth Receiver Architecture</h2>
          <p class="text-slate-400 text-sm sm:text-base mt-2 max-w-2xl">
            <span class="font-mono text-slate-200">t_wave</span> supports two distinct protocols to turn an Android device into a Bluetooth speaker—whether your device has root privileges or runs completely stock firmware.
          </p>
        </div>

        <!-- Mode Switcher Pills -->
        <div class="inline-flex p-1.5 rounded-2xl bg-slate-900 border border-slate-800 self-start">
          <button id="btnModeA2dp" onclick="switchReceiverMode('a2dp')" class="px-4 py-2.5 rounded-xl font-mono text-xs sm:text-sm font-semibold transition-all bg-cyan-500/20 text-cyan-300 border border-cyan-500/40 shadow-sm">
            (i) A2DP Sink (Root su)
          </button>
          <button id="btnModeSpp" onclick="switchReceiverMode('spp')" class="px-4 py-2.5 rounded-xl font-mono text-xs sm:text-sm font-semibold transition-all text-slate-400 hover:text-white border border-transparent">
            (ii) RFCOMM SPP (No Root)
          </button>
        </div>
      </div>

      <!-- Mode (i): A2DP Sink Panel -->
      <div id="panelModeA2dp" class="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div class="lg:col-span-7 glass-card rounded-3xl p-6 sm:p-8 space-y-6">
          <div class="flex flex-wrap items-center justify-between gap-2 border-b border-slate-800 pb-4">
            <div>
              <span class="px-2.5 py-1 rounded-lg bg-amber-500/15 text-amber-300 border border-amber-500/30 font-mono text-xs font-semibold">REQUIRES ROOT (su)</span>
              <h3 class="text-xl font-bold text-white mt-2">Standard Bluetooth Speaker (<span class="font-mono text-cyan-400">0x110B</span>)</h3>
            </div>
            <div class="text-right font-mono text-xs text-slate-400">
              <div>CoD: <span class="text-emerald-400 font-bold">0x240414</span></div>
              <div>AVRCP: <span class="text-violet-400 font-bold">0x110E</span></div>
            </div>
          </div>

          <p class="text-sm text-slate-300 leading-relaxed">
            Stock Android phones only advertise <strong>A2DP Source</strong> (sending audio out to headphones) and disable <strong>A2DP Sink</strong> (receiving audio in). When you start A2DP Sink mode, <span class="code-pill">RootBluetoothSdpService</span> delegates to <span class="code-pill">BluetoothSdpProfileConfigurator</span> and <span class="code-pill">RootA2dpController</span> using <span class="code-pill">su</span>:
          </p>

          <ul class="space-y-4 text-sm text-slate-300">
            <li class="flex items-start gap-3">
              <span class="mt-1 w-5 h-5 rounded-full bg-cyan-500/20 text-cyan-400 font-mono text-xs flex items-center justify-center shrink-0">1</span>
              <div>
                <strong class="text-white">System Properties &amp; Offload Control:</strong> Enables <span class="code-pill">bluetooth.profile.a2dp.sink.enabled</span> and <span class="code-pill">bluetooth.profile.avrcp.controller.enabled</span>, while disabling hardware offload (<span class="code-pill">persist.bluetooth.a2dp_offload.disabled=true</span>) so software PCM routing works cleanly.
              </div>
            </li>
            <li class="flex items-start gap-3">
              <span class="mt-1 w-5 h-5 rounded-full bg-cyan-500/20 text-cyan-400 font-mono text-xs flex items-center justify-center shrink-0">2</span>
              <div>
                <strong class="text-white">Bluetooth Class of Device (CoD) Patch:</strong> Changes CoD to <span class="code-pill-emerald">0x240414</span> (<code class="text-slate-300">36,4,20</code> — Rendering / Audio / Loudspeaker) and patches <span class="code-pill">bt_config.conf</span> (<code class="text-emerald-300">DevClass = 240414</code>).
              </div>
            </li>
            <li class="flex items-start gap-3">
              <span class="mt-1 w-5 h-5 rounded-full bg-cyan-500/20 text-cyan-400 font-mono text-xs flex items-center justify-center shrink-0">3</span>
              <div>
                <strong class="text-white">Clean Stack Restart &amp; SDP Discovery:</strong> Restarts <span class="code-pill">com.android.bluetooth</span> cleanly so remote phones, tablets, or PCs discover the device as a native wireless loudspeaker exposing SDP UUID <span class="code-pill">0x110B</span> (A2DP Sink) and <span class="code-pill">0x110E</span> (AVRCP Controller).
              </div>
            </li>
            <li class="flex items-start gap-3">
              <span class="mt-1 w-5 h-5 rounded-full bg-violet-500/20 text-violet-400 font-mono text-xs flex items-center justify-center shrink-0">4</span>
              <div>
                <strong class="text-white">Audio Routing (<span class="code-pill-violet">A2dpSinkAudioRouter</span>):</strong> Connects to <span class="code-pill-violet">BluetoothMediaBrowserService</span> to request/unlock audio focus (<code class="text-slate-200 font-mono text-xs">MediaController.transportControls.play()</code>), clears <span class="code-pill">A2dpSuspended</span> flags, unmutes/syncs <span class="code-pill">STREAM_MUSIC</span> volume, and bridges <span class="code-pill">TYPE_BLUETOOTH_A2DP</span> capture input to speaker playback if needed.
              </div>
            </li>
          </ul>
        </div>

        <!-- Simulated ShellDiagnosticLogHandler Terminal -->
        <div class="lg:col-span-5 glass-card rounded-3xl p-6 flex flex-col justify-between border border-slate-800">
          <div>
            <div class="flex items-center justify-between gap-2 mb-3">
              <div class="flex items-center gap-2">
                <span class="w-3 h-3 rounded-full bg-rose-500/80"></span>
                <span class="w-3 h-3 rounded-full bg-amber-500/80"></span>
                <span class="w-3 h-3 rounded-full bg-emerald-500/80"></span>
                <span class="font-mono text-xs text-slate-300 ml-1">ShellDiagnosticLogHandler</span>
              </div>
              <div class="flex items-center gap-1.5">
                <button onclick="runShellSim('success')" class="px-2.5 py-1 rounded-lg bg-emerald-500/15 hover:bg-emerald-500/25 text-emerald-300 border border-emerald-500/30 font-mono text-[11px] transition">
                  Simulate OK
                </button>
                <button onclick="runShellSim('sigkill')" class="px-2.5 py-1 rounded-lg bg-rose-500/15 hover:bg-rose-500/25 text-rose-300 border border-rose-500/30 font-mono text-[11px] transition">
                  Test Exit 137
                </button>
              </div>
            </div>

            <p class="text-xs text-slate-400 mb-3">
              Every root shell execution is traced in <span class="code-pill">ShellDiagnosticLogHandler</span> (capturing <code class="text-slate-300">stderr</code>, <code class="text-slate-300">stdout</code>, stage markers, and exit codes like <code class="text-rose-400">137/SIGKILL</code>) so failures can be inspected in the diagnostics dialog.
            </p>

            <div id="shellTerminalOutput" class="bg-slate-950 rounded-2xl p-4 border border-slate-800/90 font-mono text-xs leading-relaxed h-72 overflow-y-auto space-y-1.5 text-slate-300">
              <!-- Populated by JS -->
            </div>
          </div>

          <div class="mt-4 pt-3 border-t border-slate-800/80 flex items-center justify-between text-[11px] font-mono text-slate-500">
            <span>PIPE: su -c sh // TRACE_ACTIVE</span>
            <span id="shellExitStatus" class="text-emerald-400">EXIT_CODE: 0 (SUCCESS)</span>
          </div>
        </div>
      </div>

      <!-- Mode (ii): RFCOMM SPP Panel -->
      <div id="panelModeSpp" class="hidden grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div class="lg:col-span-7 glass-card rounded-3xl p-6 sm:p-8 space-y-6">
          <div class="flex flex-wrap items-center justify-between gap-2 border-b border-slate-800 pb-4">
            <div>
              <span class="px-2.5 py-1 rounded-lg bg-emerald-500/15 text-emerald-300 border border-emerald-500/30 font-mono text-xs font-semibold">NO ROOT REQUIRED</span>
              <h3 class="text-xl font-bold text-white mt-2">RFCOMM SPP Mode — Raw PCM Audio Receiver</h3>
            </div>
            <div class="font-mono text-xs text-cyan-400 bg-cyan-500/10 px-3 py-1.5 rounded-xl border border-cyan-500/20">
              Standard Serial Port Profile
            </div>
          </div>

          <p class="text-sm text-slate-300 leading-relaxed">
            Works out-of-the-box on unrooted Android devices by establishing a direct binary stream socket over classic Bluetooth RFCOMM:
          </p>

          <ul class="space-y-4 text-sm text-slate-300">
            <li class="flex items-start gap-3">
              <span class="mt-1 w-5 h-5 rounded-full bg-emerald-500/20 text-emerald-400 font-mono text-xs flex items-center justify-center shrink-0">1</span>
              <div>
                <strong class="text-white">RFCOMM Server Socket (<span class="code-pill-emerald">BluetoothSpeakerService</span>):</strong> Opens an RFCOMM listener bound to standard SPP UUID <span class="code-pill">00001101-0000-1000-8000-00805F9B34FB</span>. Supports both encrypted/paired and insecure/unpaired socket modes depending on your toggle.
              </div>
            </li>
            <li class="flex items-start gap-3">
              <span class="mt-1 w-5 h-5 rounded-full bg-emerald-500/20 text-emerald-400 font-mono text-xs flex items-center justify-center shrink-0">2</span>
              <div>
                <strong class="text-white">Optional BLE Presence Beacon:</strong> While listening for an incoming RFCOMM connection, <span class="font-mono text-slate-200">t_wave</span> can optionally broadcast a Bluetooth Low Energy (BLE) advertisement beacon so sender clients auto-discover the receiver immediately.
              </div>
            </li>
            <li class="flex items-start gap-3">
              <span class="mt-1 w-5 h-5 rounded-full bg-emerald-500/20 text-emerald-400 font-mono text-xs flex items-center justify-center shrink-0">3</span>
              <div>
                <strong class="text-white">Uncompressed Raw PCM Ingestion:</strong> A remote sender (such as a PC companion app or another Android phone) streams raw little-endian stereo PCM frames directly over the Bluetooth RFCOMM socket into <span class="code-pill">PcmSyncDelayLine</span> and <span class="code-pill">AudioTrack</span>.
              </div>
            </li>
          </ul>
        </div>

        <!-- Interactive RFCOMM Socket Inspector -->
        <div class="lg:col-span-5 glass-card rounded-3xl p-6 flex flex-col justify-between">
          <div class="space-y-4">
            <div class="flex items-center justify-between">
              <span class="font-mono text-xs uppercase tracking-wider text-slate-400">RFCOMM Socket Configuration</span>
              <span class="px-2 py-0.5 rounded bg-emerald-500/15 text-emerald-400 font-mono text-[11px]">LISTENING</span>
            </div>

            <div class="p-3.5 rounded-2xl bg-slate-950 border border-slate-800 font-mono text-xs space-y-2">
              <div class="text-slate-500">// Service Discovery UUID</div>
              <div class="text-cyan-300 break-all select-all">00001101-0000-1000-8000-00805F9B34FB</div>
            </div>

            <div class="grid grid-cols-2 gap-3">
              <button id="toggleSppPairing" onclick="toggleSppOption('pairing')" class="p-3.5 rounded-2xl bg-slate-900 hover:bg-slate-800/80 border border-cyan-500/40 text-left transition">
                <div class="font-mono text-[11px] text-slate-400">SOCKET SECURITY</div>
                <div id="sppPairingLabel" class="font-mono text-sm font-bold text-cyan-300 mt-1">Paired (RFCOMM)</div>
              </button>
              <button id="toggleSppBle" onclick="toggleSppOption('ble')" class="p-3.5 rounded-2xl bg-slate-900 hover:bg-slate-800/80 border border-violet-500/40 text-left transition">
                <div class="font-mono text-[11px] text-slate-400">BLE PRESENCE BEACON</div>
                <div id="sppBleLabel" class="font-mono text-sm font-bold text-violet-300 mt-1">Broadcasting ON</div>
              </button>
            </div>

            <div class="p-4 rounded-2xl bg-slate-900/70 border border-slate-800/90 text-xs text-slate-300 space-y-1.5">
              <div class="font-mono text-slate-400">PACKET FRAMING SPEC:</div>
              <div>• Byte Order: <span class="font-mono text-emerald-300">ByteOrder.LITTLE_ENDIAN</span></div>
              <div>• Channel Layout: <span class="font-mono text-cyan-300">CHANNEL_OUT_STEREO (L/R Interleaved)</span></div>
              <div>• Transport Overhead: <span class="font-mono text-slate-200">Zero codec re-compression loss</span></div>
            </div>
          </div>

          <div class="mt-4 pt-3 border-t border-slate-800 text-[11px] font-mono text-slate-500 flex justify-between">
            <span>SERVICE: BluetoothSpeakerService</span>
            <span class="text-emerald-400">ROOT: NOT_REQUIRED</span>
          </div>
        </div>
      </div>
    </section>

    <!-- SECTION 2: High-Res AudioTrack, Latency Modes & Sync Offset -->
    <section id="audio-engine" class="scroll-mt-24">
      <div class="mb-8">
        <span class="font-mono text-xs font-semibold uppercase tracking-widest text-violet-400">02 // PCM AUDIO ENGINE &amp; TIMING</span>
        <h2 class="text-2xl sm:text-3xl font-bold text-white mt-1">High-Res AudioTrack, Latency Modes &amp; Sync Offset</h2>
        <p class="text-slate-400 text-sm sm:text-base mt-2 max-w-3xl">
          Configure sample formats, HAL-burst buffer profiles, FastMixer paths, and test the inline circular <span class="code-pill">PcmSyncDelayLine</span> buffer interactively below.
        </p>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <!-- PcmAudioTrackConfigurator Interactive Panel -->
        <div class="lg:col-span-7 glass-card rounded-3xl p-6 sm:p-8 space-y-6">
          <div class="flex items-center justify-between border-b border-slate-800 pb-4">
            <div>
              <h3 class="text-lg sm:text-xl font-bold text-white font-mono">PcmAudioTrackConfigurator</h3>
              <p class="text-xs text-slate-400 mt-0.5">Select PCM stream resolution &amp; buffer tuning profile</p>
            </div>
            <span id="pcmFloatBadge" class="hidden px-2.5 py-1 rounded-lg bg-violet-500/15 text-violet-300 border border-violet-500/30 font-mono text-[11px]">
              Legacy API → ENCODING_PCM_FLOAT
            </span>
          </div>

          <!-- Sample Rate & Bit Depth Selector -->
          <div>
            <label class="block font-mono text-xs uppercase tracking-wider text-slate-400 mb-2.5">1. PCM Sample Rate &amp; Bit Depth</label>
            <div class="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
              <button onclick="selectPcmFormat(44100, 16, this)" class="pcm-fmt-btn p-3 rounded-xl border text-left transition bg-slate-900/60 border-slate-800 hover:border-slate-700">
                <div class="font-mono text-sm font-bold text-white">44.1 kHz</div>
                <div class="font-mono text-xs text-slate-400">16-bit PCM</div>
              </button>
              <button onclick="selectPcmFormat(48000, 24, this)" class="pcm-fmt-btn p-3 rounded-xl border text-left transition bg-cyan-500/15 border-cyan-500/50">
                <div class="font-mono text-sm font-bold text-cyan-300">48 kHz</div>
                <div class="font-mono text-xs text-cyan-200/80">24-bit PCM</div>
              </button>
              <button onclick="selectPcmFormat(96000, 24, this)" class="pcm-fmt-btn p-3 rounded-xl border text-left transition bg-slate-900/60 border-slate-800 hover:border-slate-700">
                <div class="font-mono text-sm font-bold text-white">96 kHz</div>
                <div class="font-mono text-xs text-slate-400">24-bit Hi-Res</div>
              </button>
              <button onclick="selectPcmFormat(192000, 32, this)" class="pcm-fmt-btn p-3 rounded-xl border text-left transition bg-slate-900/60 border-slate-800 hover:border-slate-700">
                <div class="font-mono text-sm font-bold text-white">192 kHz</div>
                <div class="font-mono text-xs text-slate-400">32-bit Studio</div>
              </button>
            </div>
            <p class="text-xs text-slate-400 mt-2">
              Automatically converts 24/32-bit packed PCM to <span class="code-pill">ENCODING_PCM_FLOAT</span> on older Android versions if native 24/32-bit hardware tracks aren't exposed.
            </p>
          </div>

          <!-- Buffer Profile Selector -->
          <div>
            <label class="block font-mono text-xs uppercase tracking-wider text-slate-400 mb-2.5">2. Ring Buffer Profile (Frame &amp; HAL-Burst Aligned)</label>
            <div class="grid grid-cols-3 gap-2.5">
              <button onclick="selectBufferProfile('LOW_LATENCY', this)" class="buf-prof-btn p-3 rounded-xl border text-center font-mono text-xs font-semibold transition bg-slate-900/60 border-slate-800 text-slate-300">
                LOW_LATENCY
              </button>
              <button onclick="selectBufferProfile('BALANCED', this)" class="buf-prof-btn p-3 rounded-xl border text-center font-mono text-xs font-semibold transition bg-violet-500/15 border-violet-500/50 text-violet-200">
                BALANCED
              </button>
              <button onclick="selectBufferProfile('HIRES_DEEP', this)" class="buf-prof-btn p-3 rounded-xl border text-center font-mono text-xs font-semibold transition bg-slate-900/60 border-slate-800 text-slate-300">
                HIRES_DEEP
              </button>
            </div>
          </div>

          <!-- Latency Mode Switches -->
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div class="p-4 rounded-2xl bg-slate-900/80 border border-slate-800 flex items-start justify-between gap-3">
              <div>
                <div class="font-semibold text-sm text-white">Low Latency Mode</div>
                <p class="text-xs text-slate-400 mt-1">Requests Android's FastMixer path (<span class="code-pill">PERFORMANCE_MODE_LOW_LATENCY</span>) with trimmed buffers.</p>
              </div>
              <input id="chkLowLatency" type="checkbox" checked onchange="recalcAudioEngine()" class="mt-1 w-5 h-5 accent-cyan-400 rounded cursor-pointer">
            </div>

            <div class="p-4 rounded-2xl bg-slate-900/80 border border-slate-800 flex items-start justify-between gap-3">
              <div>
                <div class="font-semibold text-sm text-white">Ultra-Low Latency</div>
                <p class="text-xs text-slate-400 mt-1">Drops to <strong class="text-emerald-300">1× HAL burst</strong> sizing, <strong class="text-emerald-300">512-byte</strong> chunk reads, &amp; elevated audio thread priority.</p>
              </div>
              <input id="chkUltraLowLatency" type="checkbox" onchange="recalcAudioEngine()" class="mt-1 w-5 h-5 accent-emerald-400 rounded cursor-pointer">
            </div>
          </div>

          <!-- Live Calculated Metrics Strip -->
          <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-2">
            <div class="p-3 rounded-xl bg-slate-950 border border-slate-800">
              <div class="font-mono text-[10px] text-slate-500">RAW BITRATE</div>
              <div id="calcBitrate" class="font-mono text-base font-bold text-cyan-400 mt-0.5">2,304 kbps</div>
            </div>
            <div class="p-3 rounded-xl bg-slate-950 border border-slate-800">
              <div class="font-mono text-[10px] text-slate-500">FRAME SIZE</div>
              <div id="calcFrameBytes" class="font-mono text-base font-bold text-slate-200 mt-0.5">6 Bytes/fr</div>
            </div>
            <div class="p-3 rounded-xl bg-slate-950 border border-slate-800">
              <div class="font-mono text-[10px] text-slate-500">RING BUFFER</div>
              <div id="calcBufferBytes" class="font-mono text-base font-bold text-violet-400 mt-0.5">7,680 B</div>
            </div>
            <div class="p-3 rounded-xl bg-slate-950 border border-slate-800">
              <div class="font-mono text-[10px] text-slate-500">TARGET LATENCY</div>
              <div id="calcLatencyMs" class="font-mono text-base font-bold text-emerald-400 mt-0.5">26.7 ms</div>
            </div>
          </div>
        </div>

        <!-- PcmSyncDelayLine Circular Buffer Simulator -->
        <div class="lg:col-span-5 glass-card rounded-3xl p-6 sm:p-8 flex flex-col justify-between space-y-6">
          <div class="space-y-4">
            <div class="flex items-center justify-between border-b border-slate-800 pb-4">
              <div>
                <h3 class="text-lg sm:text-xl font-bold text-white font-mono">PcmSyncDelayLine</h3>
                <p class="text-xs text-slate-400 mt-0.5">Manual Audio Sync Offset (-250 ms to +500 ms)</p>
              </div>
              <span id="syncOffsetBadge" class="px-3 py-1 rounded-xl bg-cyan-500/15 text-cyan-300 border border-cyan-500/30 font-mono text-sm font-bold">
                0 ms
              </span>
            </div>

            <p class="text-xs sm:text-sm text-slate-300 leading-relaxed">
              Sits inline ahead of <span class="code-pill">AudioTrack.write(...)</span> as a frame-aligned circular PCM buffer adjustable in <strong>5 ms steps</strong>:
            </p>

            <!-- Interactive Slider -->
            <div class="p-4 rounded-2xl bg-slate-950 border border-slate-800 space-y-3">
              <div class="flex justify-between font-mono text-xs text-slate-400">
                <span>-250 ms (Advance)</span>
                <button onclick="setSyncOffset(0)" class="text-cyan-400 hover:underline">Reset 0ms</button>
                <span>+500 ms (Delay)</span>
              </div>
              <input id="syncOffsetSlider" type="range" min="-250" max="500" step="5" value="0" oninput="updateSyncOffset(this.value)" class="w-full" />

              <!-- Visual Circular Buffer Bar -->
              <div class="pt-2">
                <div class="flex justify-between font-mono text-[11px] text-slate-400 mb-1.5">
                  <span>Circular PCM Backlog Queue</span>
                  <span id="syncQueuedFrames" class="text-slate-200">0 frames (0 KB)</span>
                </div>
                <div class="w-full h-4 rounded-full bg-slate-900 border border-slate-800 overflow-hidden flex items-center p-0.5">
                  <div id="syncBufferFillBar" class="h-full rounded-full bg-gradient-to-r from-cyan-400 to-violet-500 transition-all duration-150" style="width: 33%"></div>
                </div>
              </div>
            </div>

            <!-- Dynamic Explanation Box -->
            <div id="syncBehaviorBox" class="p-4 rounded-2xl bg-slate-900/90 border border-slate-800 text-xs sm:text-sm text-slate-300 leading-relaxed">
              <strong class="text-cyan-300 font-mono">0 ms (Pass-Through):</strong> Incoming stereo PCM frames pass directly to <span class="code-pill">AudioTrack.write(...)</span> with zero artificial delay or frame dropping.
            </div>
          </div>

          <div class="grid grid-cols-2 gap-2.5 pt-2">
            <button onclick="setSyncOffset(140)" class="py-2 px-3 rounded-xl bg-violet-500/15 hover:bg-violet-500/25 border border-violet-500/30 text-violet-200 font-mono text-xs transition">
              Preset: +140ms (Video Sync)
            </button>
            <button onclick="setSyncOffset(-65)" class="py-2 px-3 rounded-xl bg-amber-500/15 hover:bg-amber-500/25 border border-amber-500/30 text-amber-200 font-mono text-xs transition">
              Preset: -65ms (Catch-Up)
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- SECTION 3: Lock Screen & Continuous Background Playback -->
    <section id="lock-screen" class="scroll-mt-24">
      <div class="mb-8">
        <span class="font-mono text-xs font-semibold uppercase tracking-widest text-emerald-400">03 // FOREGROUND RESILIENCE &amp; LOCK-SCREEN CONSOLE</span>
        <h2 class="text-2xl sm:text-3xl font-bold text-white mt-1">Lock Screen &amp; Continuous Background Playback</h2>
        <p class="text-slate-400 text-sm sm:text-base mt-2 max-w-3xl">
          Engineered so wireless audio never glitches when the screen turns off, with instant lock-screen transport controls and automatic connection recovery.
        </p>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
        <!-- Technical Breakdown Cards -->
        <div class="lg:col-span-7 space-y-4">
          <div class="glass-card rounded-2xl p-5 flex items-start gap-4">
            <div class="w-10 h-10 rounded-xl bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center shrink-0 text-emerald-400 font-mono font-bold">
              CPU
            </div>
            <div>
              <h3 class="font-bold text-white text-base">Foreground Service &amp; Partial Wake Lock</h3>
              <p class="text-sm text-slate-300 mt-1 leading-relaxed">
                <span class="code-pill">BluetoothSpeakerService</span> runs as a <span class="code-pill-emerald">FOREGROUND_SERVICE_MEDIA_PLAYBACK</span> foreground service holding a <span class="code-pill">PARTIAL_WAKE_LOCK</span> (<code class="text-slate-300 font-mono text-xs">WAKE_LOCK</code>), preventing CPU deep-sleep and audio dropouts when the screen locks.
              </p>
            </div>
          </div>

          <div class="glass-card rounded-2xl p-5 flex items-start gap-4">
            <div class="w-10 h-10 rounded-xl bg-cyan-500/15 border border-cyan-500/30 flex items-center justify-center shrink-0 text-cyan-400 font-mono font-bold">
              UI
            </div>
            <div>
              <h3 class="font-bold text-white text-base">Public MediaSession &amp; Lock-Screen Visibility</h3>
              <p class="text-sm text-slate-300 mt-1 leading-relaxed">
                Publishes a public <span class="code-pill">MediaSession</span> (<span class="code-pill">Notification.MediaStyle</span>, <span class="code-pill">VISIBILITY_PUBLIC</span>) with active playback state and quick <strong>Mute/Unmute</strong> and <strong>Stop</strong> actions directly on the lock screen. When <em>Work on Lock Screen</em> is active, <span class="code-pill">MainActivity</span> calls <span class="code-pill">setShowWhenLocked(true)</span> so the full visualizer console remains accessible without unlocking.
              </p>
            </div>
          </div>

          <div class="glass-card rounded-2xl p-5 flex items-start gap-4">
            <div class="w-10 h-10 rounded-xl bg-violet-500/15 border border-violet-500/30 flex items-center justify-center shrink-0 text-violet-400 font-mono font-bold">
              5×
            </div>
            <div>
              <h3 class="font-bold text-white text-base">5-Attempt Auto-Reconnect Loop</h3>
              <p class="text-sm text-slate-300 mt-1 leading-relaxed">
                If the remote Bluetooth link drops due to interference or range, <strong>Auto-Reconnect</strong> automatically retries connecting to the last paired/connected device up to <strong>5 times</strong> before cleanly returning to standby.
              </p>
            </div>
          </div>
        </div>

        <!-- Interactive Android Lock Screen Mockup -->
        <div class="lg:col-span-5 flex justify-center">
          <div class="w-full max-w-sm rounded-[2.25rem] bg-slate-950 p-3 border-2 border-slate-800 shadow-2xl relative">
            <!-- Phone Notch -->
            <div class="w-28 h-5 bg-slate-900 rounded-b-2xl mx-auto mb-3 border-x border-b border-slate-800"></div>

            <!-- Lock Screen Wallpaper Area -->
            <div id="lockScreenWallpaper" class="rounded-[1.6rem] bg-gradient-to-b from-slate-900 via-obsidian to-slate-950 p-5 border border-slate-800/80 min-h-[400px] flex flex-col justify-between transition-all">
              <!-- Clock & Lock Icon -->
              <div class="text-center pt-2">
                <div class="inline-flex items-center gap-1.5 text-slate-400 text-xs font-mono mb-1">
                  <svg class="w-3.5 h-3.5 text-cyan-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                  </svg>
                  <span id="lockWindowStatus">VISIBILITY_PUBLIC</span>
                </div>
                <div class="text-4xl font-light tracking-tight text-white font-sans">14:09</div>
                <div class="text-xs text-slate-400 mt-0.5">Wednesday, Nov 18</div>
              </div>

              <!-- Dynamic Overlay if setShowWhenLocked(true) toggled -->
              <div id="showWhenLockedConsole" class="hidden my-3 p-3.5 rounded-2xl bg-cyan-950/40 border border-cyan-500/40 text-center space-y-2">
                <div class="font-mono text-[11px] text-cyan-300 uppercase">MainActivity // setShowWhenLocked(true)</div>
                <div class="text-xs text-slate-200">Full t_wave Signal Scope Console active over lock screen without PIN unlock!</div>
              </div>

              <!-- Notification.MediaStyle Card -->
              <div class="rounded-2xl bg-slate-900/95 border border-slate-700/80 p-4 shadow-lg space-y-3">
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-2">
                    <span class="w-2 h-2 rounded-full bg-cyan-400 animate-pulse"></span>
                    <span class="font-mono text-[11px] font-semibold text-cyan-300">t_wave • MediaSession</span>
                  </div>
                  <span id="lockMediaStateBadge" class="font-mono text-[10px] px-2 py-0.5 rounded bg-emerald-500/15 text-emerald-300">PLAYING</span>
                </div>

                <div>
                  <div class="text-sm font-bold text-white">MacBook Pro — A2DP / PCM Sink</div>
                  <div class="text-xs text-slate-400 font-mono">48 kHz / 24-bit • PARTIAL_WAKE_LOCK</div>
                </div>

                <!-- Interactive MediaStyle Action Buttons -->
                <div class="flex items-center justify-between pt-2 border-t border-slate-800 gap-2">
                  <button onclick="toggleLockMute()" id="btnLockMute" class="flex-1 py-2 px-3 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs font-mono font-semibold text-white transition">
                    Mute
                  </button>
                  <button onclick="toggleLockStop()" id="btnLockStop" class="flex-1 py-2 px-3 rounded-xl bg-rose-500/20 hover:bg-rose-500/30 border border-rose-500/30 text-xs font-mono font-semibold text-rose-200 transition">
                    Stop
                  </button>
                </div>
              </div>

              <!-- Toggle Work on Lock Screen -->
              <div class="pt-3 flex items-center justify-between px-1">
                <span class="font-mono text-[11px] text-slate-400">Work on Lock Screen:</span>
                <button onclick="toggleShowWhenLocked()" id="btnShowWhenLocked" class="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-cyan-300 font-mono text-[11px] border border-slate-700 transition">
                  Enable Activity Overlay
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- SECTION 4: Real-Time Telemetry & Signal Scope Visualizations -->
    <section id="signal-scope" class="scroll-mt-24">
      <div class="flex flex-col lg:flex-row lg:items-end justify-between gap-4 mb-8">
        <div>
          <span class="font-mono text-xs font-semibold uppercase tracking-widest text-cyan-400">04 // PCM ANALYZER &amp; 8 CANVAS ENGINES</span>
          <h2 class="text-2xl sm:text-3xl font-bold text-white mt-1">Real-Time Telemetry &amp; Signal Scope Visualizations</h2>
          <p class="text-slate-400 text-sm sm:text-base mt-2 max-w-3xl">
            <span class="code-pill">PcmAnalyzer</span> aligns incoming byte chunks to exact stereo frame boundaries, computes Left/Right RMS VU levels, populates 28 spectrum bands, and drives all 8 <span class="code-pill">ScopeAnimationRenderers</span>.
          </p>
        </div>

        <!-- Optional Web Audio Synth Sound Toggle -->
        <div class="flex items-center gap-2.5 self-start">
          <button id="btnToggleSynth" onclick="toggleWebAudioSynth()" class="px-4 py-2.5 rounded-xl bg-violet-600/20 hover:bg-violet-600/30 border border-violet-500/40 text-violet-200 font-mono text-xs font-semibold transition flex items-center gap-2">
            <svg class="w-4 h-4 text-violet-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
            </svg>
            <span id="synthBtnLabel">Audio Synth: OFF (Visual Sim)</span>
          </button>
        </div>
      </div>

      <div class="glass-card rounded-3xl p-5 sm:p-8 border border-slate-800 space-y-6">
        <!-- 8 Animation Mode Selector Pills -->
        <div>
          <div class="font-mono text-xs uppercase tracking-wider text-slate-400 mb-2.5">Select ScopeAnimationRenderer (8 Modes):</div>
          <div id="scopeModeButtons" class="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-2">
            <!-- Buttons injected by JS -->
          </div>
        </div>

        <!-- Main Interactive Signal Scope Canvas -->
        <div class="relative rounded-2xl bg-slate-950 border border-slate-800/90 overflow-hidden shadow-inner">
          <!-- Top Overlay Status Strip -->
          <div class="flex flex-wrap items-center justify-between gap-2 px-4 py-2.5 bg-slate-900/70 border-b border-slate-800/80 font-mono text-xs">
            <div class="flex items-center gap-2">
              <span id="scopeConnDot" class="w-2.5 h-2.5 rounded-full bg-emerald-400"></span>
              <span class="text-slate-300">STATE: <strong id="scopeConnLabel" class="text-emerald-400">CONNECTED</strong></span>
              <span id="scopeMuteBadge" class="hidden px-2 py-0.5 rounded bg-rose-500/20 text-rose-300 text-[10px]">MUTED</span>
            </div>
            <div class="flex items-center gap-4 text-slate-400">
              <span>THROUGHPUT: <strong id="telThroughput" class="text-cyan-300">2,304 kbps</strong></span>
              <span>BUFFER FILL: <strong id="telBufferFill" class="text-violet-300">68%</strong></span>
              <span>EFF. LATENCY: <strong id="telLatency" class="text-emerald-300">24.2 ms</strong></span>
            </div>
          </div>

          <!-- Responsive Canvas Container -->
          <div class="w-full h-64 sm:h-80 relative">
            <canvas id="signalScopeCanvas" class="w-full h-full block"></canvas>
          </div>

          <!-- Bottom Stereo L/R RMS VU Meters -->
          <div class="px-4 py-3 bg-slate-900/80 border-t border-slate-800/80 grid grid-cols-1 sm:grid-cols-2 gap-3 font-mono text-xs">
            <div class="flex items-center gap-3">
              <span class="text-cyan-400 font-bold w-8">L RMS</span>
              <div class="flex-1 h-2.5 rounded-full bg-slate-950 border border-slate-800 overflow-hidden">
                <div id="vuBarLeft" class="h-full bg-gradient-to-r from-emerald-400 via-cyan-400 to-violet-500 transition-all duration-75" style="width: 60%"></div>
              </div>
              <span id="vuDbLeft" class="text-slate-400 w-14 text-right">-6.2 dB</span>
            </div>
            <div class="flex items-center gap-3">
              <span class="text-violet-400 font-bold w-8">R RMS</span>
              <div class="flex-1 h-2.5 rounded-full bg-slate-950 border border-slate-800 overflow-hidden">
                <div id="vuBarRight" class="h-full bg-gradient-to-r from-emerald-400 via-cyan-400 to-violet-500 transition-all duration-75" style="width: 56%"></div>
              </div>
              <span id="vuDbRight" class="text-slate-400 w-14 text-right">-6.8 dB</span>
            </div>
          </div>
        </div>

        <!-- Connection State & Mute Simulation Controls -->
        <div class="flex flex-wrap items-center justify-between gap-3 pt-1">
          <div class="flex flex-wrap items-center gap-2">
            <span class="font-mono text-xs text-slate-400 mr-1">Simulate State:</span>
            <button onclick="setScopeState('Idle')" class="scope-state-btn px-3 py-1.5 rounded-xl font-mono text-xs bg-slate-900 border border-slate-800 text-slate-300 hover:border-slate-600">Idle</button>
            <button onclick="setScopeState('Listening')" class="scope-state-btn px-3 py-1.5 rounded-xl font-mono text-xs bg-slate-900 border border-slate-800 text-slate-300 hover:border-slate-600">Listening</button>
            <button onclick="setScopeState('Reconnecting')" class="scope-state-btn px-3 py-1.5 rounded-xl font-mono text-xs bg-slate-900 border border-slate-800 text-slate-300 hover:border-slate-600">Reconnecting</button>
            <button onclick="setScopeState('Connected')" class="scope-state-btn px-3 py-1.5 rounded-xl font-mono text-xs bg-emerald-500/20 border border-emerald-500/40 text-emerald-300">Connected</button>
          </div>

          <button id="btnScopeMute" onclick="toggleScopeMute()" class="px-4 py-1.5 rounded-xl font-mono text-xs bg-slate-900 hover:bg-slate-800 border border-slate-700 text-slate-200 transition">
            Toggle Mute State
          </button>
        </div>
      </div>
    </section>

    <!-- SECTION 5: State Management & Persistence Architecture -->
    <section id="state-persistence" class="scroll-mt-24">
      <div class="mb-8">
        <span class="font-mono text-xs font-semibold uppercase tracking-widest text-amber-400">05 // REACTIVE ARCHITECTURE &amp; DATASTORE</span>
        <h2 class="text-2xl sm:text-3xl font-bold text-white mt-1">State Management &amp; Persistence</h2>
        <p class="text-slate-400 text-sm sm:text-base mt-2 max-w-3xl">
          Unidirectional reactive state flows seamlessly between background foreground services, ViewModels, Jetpack Compose UI, and Jetpack DataStore.
        </p>
      </div>

      <!-- Architectural Flow Diagram -->
      <div class="glass-card rounded-3xl p-6 sm:p-8 border border-slate-800">
        <div class="grid grid-cols-1 md:grid-cols-3 gap-6 items-stretch">
          <!-- Column 1: Background Services -->
          <div class="rounded-2xl bg-slate-950/90 border border-slate-800 p-5 flex flex-col justify-between">
            <div>
              <div class="font-mono text-[11px] uppercase tracking-wider text-cyan-400 mb-2">Background Services</div>
              <h3 class="text-base font-bold text-white mb-3">Hardware &amp; Socket Layer</h3>
              <ul class="space-y-2 text-xs text-slate-300 font-mono">
                <li class="p-2 rounded-lg bg-slate-900 border border-slate-800">BluetoothSpeakerService</li>
                <li class="p-2 rounded-lg bg-slate-900 border border-slate-800">RootBluetoothSdpService</li>
                <li class="p-2 rounded-lg bg-slate-900 border border-slate-800">PcmAnalyzer (Telemetry)</li>
              </ul>
            </div>
            <div class="mt-4 pt-3 border-t border-slate-800/80 font-mono text-[11px] text-slate-500">
              Emits state updates &amp; RMS frames →
            </div>
          </div>

          <!-- Column 2: Central Reactive Hub -->
          <div class="rounded-2xl bg-gradient-to-b from-cyan-950/30 via-slate-950 to-violet-950/30 border border-cyan-500/40 p-5 flex flex-col justify-between shadow-lg shadow-cyan-500/5">
            <div>
              <div class="font-mono text-[11px] uppercase tracking-wider text-emerald-400 mb-2">Single Source of Truth</div>
              <h3 class="text-lg font-bold text-white font-mono mb-2">SpeakerSessionManager</h3>
              <p class="text-xs text-slate-300 leading-relaxed mb-4">
                Exposes an immutable reactive <span class="code-pill">StateFlow&lt;SpeakerServiceState&gt;</span> shared across background services, <span class="code-pill">SpeakerViewModel</span>, and the Jetpack Compose UI (<span class="code-pill">SpeakerScreen</span>).
              </p>
            </div>
            <div class="p-3 rounded-xl bg-slate-900/90 border border-slate-800 font-mono text-[11px] text-cyan-300 text-center">
              StateFlow&lt;SpeakerServiceState&gt;
            </div>
          </div>

          <!-- Column 3: Jetpack DataStore Persistence -->
          <div class="rounded-2xl bg-slate-950/90 border border-slate-800 p-5 flex flex-col justify-between">
            <div>
              <div class="font-mono text-[11px] uppercase tracking-wider text-violet-400 mb-2">Jetpack DataStore</div>
              <h3 class="text-base font-bold text-white font-mono mb-2">SpeakerPreferences</h3>
              <p class="text-xs text-slate-400 mb-3">Persists user &amp; system state across app restarts:</p>
              <div class="flex flex-wrap gap-1.5">
                <span class="code-pill-violet">Speaker Protocol</span>
                <span class="code-pill-violet">Original CoD Backup</span>
                <span class="code-pill-violet">Last Connected Device</span>
                <span class="code-pill-violet">PCM Quality &amp; Buffer</span>
                <span class="code-pill-violet">Latency Toggles</span>
                <span class="code-pill-violet">Sync Offset (ms)</span>
                <span class="code-pill-violet">Lock-Screen Pref</span>
                <span class="code-pill-violet">Signal Scope Mode</span>
              </div>
            </div>
            <div class="mt-4 pt-3 border-t border-slate-800/80 font-mono text-[11px] text-slate-500">
              ← Restores on boot / launch
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- SECTION 6: About t_wave & Creator Attribution -->
    <section id="about" class="scroll-mt-24">
      <div class="mb-8">
        <span class="font-mono text-xs font-semibold uppercase tracking-widest text-cyan-400">06 // PROJECT OVERVIEW &amp; CREDITS</span>
        <h2 class="text-2xl sm:text-3xl font-bold text-white mt-1">About t_wave</h2>
      </div>

      <div class="glass-card rounded-3xl p-6 sm:p-10 border border-slate-800/90 relative overflow-hidden">
        <!-- Subtle Decorative Glow -->
        <div class="pointer-events-none absolute -right-20 -bottom-20 w-80 h-80 bg-cyan-500/10 rounded-full blur-3xl"></div>
        <div class="pointer-events-none absolute -left-20 -top-20 w-72 h-72 bg-violet-600/10 rounded-full blur-3xl"></div>

        <div class="relative grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
          <div class="lg:col-span-7 space-y-4">
            <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-cyan-500/10 border border-cyan-500/25 text-cyan-300 font-mono text-xs">
              <span>HIGH-RES WIRELESS AUDIO RECEIVER FOR ANDROID</span>
            </div>
            <h3 class="text-xl sm:text-2xl font-bold text-white leading-snug">
              Built to Unlock Dedicated Wireless Loudspeaker Capabilities on Android
            </h3>
            <p class="text-sm sm:text-base text-slate-300 leading-relaxed">
              <strong class="font-mono text-white">t_wave</strong> was engineered to bridge the gap between consumer Android hardware and studio-grade wireless audio reception. Whether running rooted A2DP Sink SDP reconfiguration (<span class="code-pill">0x110B</span>) for universal plug-and-play speaker discovery or streaming uncompressed raw stereo PCM over RFCOMM SPP on stock devices, every stage of the pipeline is tuned for deterministic latency, continuous lock-screen resilience, and real-time DSP telemetry.
            </p>
            <div class="flex flex-wrap gap-2 pt-1 font-mono text-xs text-slate-400">
              <span class="px-2.5 py-1 rounded-lg bg-slate-900 border border-slate-800">Kotlin &amp; Jetpack Compose</span>
              <span class="px-2.5 py-1 rounded-lg bg-slate-900 border border-slate-800">Android AudioTrack HAL</span>
              <span class="px-2.5 py-1 rounded-lg bg-slate-900 border border-slate-800">Bluetooth SDP &amp; RFCOMM</span>
            </div>
          </div>

          <!-- Creator Attribution Card -->
          <div class="lg:col-span-5">
            <div class="rounded-2xl bg-slate-950/90 border border-slate-800 p-6 space-y-4 shadow-xl">
              <div class="flex items-center gap-3.5">
                <div class="w-12 h-12 rounded-2xl bg-gradient-to-br from-cyan-400 to-violet-600 p-[1px] shrink-0">
                  <div class="w-full h-full bg-slate-950 rounded-[15px] flex items-center justify-center font-mono font-bold text-cyan-300 text-base">
                    TRS
                  </div>
                </div>
                <div>
                  <div class="font-mono text-[11px] uppercase tracking-wider text-slate-400">Creator &amp; Developer</div>
                  <div class="text-base sm:text-lg font-bold text-white">Tapas Ranjan Sahu</div>
                </div>
              </div>

              <div class="p-3.5 rounded-xl bg-slate-900/90 border border-slate-800/90 font-mono text-xs sm:text-sm text-slate-200">
                A project by - <strong class="text-white">Tapas Ranjan Sahu</strong>
                <a href="https://t.me/xenon3x" target="_blank" rel="noopener noreferrer" class="inline-flex items-center gap-1 ml-1 text-cyan-400 hover:text-cyan-300 underline decoration-cyan-500/40 underline-offset-4 transition-colors">
                  <span>@xenon3x</span>
                </a>
                <span class="text-slate-400">-tg</span>
              </div>

              <a href="https://t.me/xenon3x" target="_blank" rel="noopener noreferrer" class="w-full py-2.5 px-4 rounded-xl bg-cyan-500/15 hover:bg-cyan-500/25 border border-cyan-500/35 text-cyan-300 font-mono text-xs font-semibold transition-all flex items-center justify-center gap-2">
                <svg class="w-4 h-4 text-cyan-400" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M11.944 0A12 12 0 0 0 0 12a12 12 0 0 0 12 12 12 12 0 0 0 12-12A12 12 0 0 0 12 0a12 12 0 0 0-.056 0zm4.962 7.224c.1-.002.321.023.465.14a.506.506 0 0 1 .171.325c.016.093.036.306.02.472-.18 1.898-.962 6.502-1.36 8.627-.168.9-.499 1.201-.82 1.23-.696.065-1.225-.46-1.9-.902-1.056-.693-1.653-1.124-2.678-1.8-1.185-.78-.417-1.21.258-1.91.177-.184 3.247-2.977 3.307-3.23.007-.032.014-.15-.056-.212s-.174-.041-.249-.024c-.106.024-1.793 1.14-5.061 3.345-.48.33-.913.49-1.302.48-.428-.008-1.252-.241-1.865-.44-.752-.245-1.349-.374-1.297-.789.027-.216.325-.437.893-.663 3.498-1.524 5.83-2.529 6.998-3.014 3.332-1.386 4.025-1.627 4.476-1.635z"/>
                </svg>
                <span>Connect on Telegram (@xenon3x -tg)</span>
              </a>
            </div>
          </div>
        </div>
      </div>
    </section>

  </main>

  <!-- Footer -->
  <footer class="border-t border-slate-800/80 bg-slate-950/80 py-10 mt-12">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col md:flex-row items-center justify-between gap-4 text-xs font-mono text-slate-400">
      <div class="flex flex-wrap items-center justify-center md:justify-start gap-2">
        <span class="text-white font-bold">t_wave</span>
        <span class="text-slate-600">//</span>
        <span>
          A project by - <strong class="text-slate-200">Tapas Ranjan Sahu</strong>
          <a href="https://t.me/xenon3x" target="_blank" rel="noopener noreferrer" class="text-cyan-400 hover:text-cyan-300 underline decoration-cyan-500/40 underline-offset-4 transition-colors">@xenon3x</a>
          <span>-tg</span>
        </span>
      </div>
      <div class="text-slate-500 text-center md:text-right">
        <span>A2DP Sink (0x110B) • RFCOMM SPP • 192kHz/32-bit PCM • Signal Scope</span>
      </div>
    </div>
  </footer>

  <!-- Interactive JavaScript Logic -->
  <script>
    // =========================================================================
    // 1. Dual Receiver Mode Switcher & ShellDiagnosticLogHandler Simulator
    // =========================================================================
    function switchReceiverMode(mode) {
      const btnA2dp = document.getElementById('btnModeA2dp');
      const btnSpp = document.getElementById('btnModeSpp');
      const panelA2dp = document.getElementById('panelModeA2dp');
      const panelSpp = document.getElementById('panelModeSpp');

      if (mode === 'a2dp') {
        btnA2dp.className = 'px-4 py-2.5 rounded-xl font-mono text-xs sm:text-sm font-semibold transition-all bg-cyan-500/20 text-cyan-300 border border-cyan-500/40 shadow-sm';
        btnSpp.className = 'px-4 py-2.5 rounded-xl font-mono text-xs sm:text-sm font-semibold transition-all text-slate-400 hover:text-white border border-transparent';
        panelA2dp.classList.remove('hidden');
        panelSpp.classList.add('hidden');
      } else {
        btnSpp.className = 'px-4 py-2.5 rounded-xl font-mono text-xs sm:text-sm font-semibold transition-all bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 shadow-sm';
        btnA2dp.className = 'px-4 py-2.5 rounded-xl font-mono text-xs sm:text-sm font-semibold transition-all text-slate-400 hover:text-white border border-transparent';
        panelSpp.classList.remove('hidden');
        panelA2dp.classList.add('hidden');
      }
    }

    function runShellSim(type) {
      const term = document.getElementById('shellTerminalOutput');
      const status = document.getElementById('shellExitStatus');
      term.innerHTML = '';

      const logsSuccess = [
        { tag: '[STAGE:INIT]', color: 'text-cyan-400', text: 'RootBluetoothSdpService -> Requesting su shell...' },
        { tag: '[STDOUT]', color: 'text-slate-300', text: 'uid=0(root) gid=0(root) context=u:r:magisk:s0' },
        { tag: '[STAGE:PROPS]', color: 'text-cyan-400', text: 'setprop bluetooth.profile.a2dp.sink.enabled true' },
        { tag: '[STAGE:PROPS]', color: 'text-cyan-400', text: 'setprop bluetooth.profile.avrcp.controller.enabled true' },
        { tag: '[STAGE:PROPS]', color: 'text-cyan-400', text: 'setprop persist.bluetooth.a2dp_offload.disabled true' },
        { tag: '[STAGE:COD]', color: 'text-emerald-400', text: 'Backing up original CoD -> SpeakerPreferences' },
        { tag: '[STAGE:COD]', color: 'text-emerald-400', text: 'Patching bt_config.conf: DevClass = 240414 (0x240414)' },
        { tag: '[STAGE:SDP]', color: 'text-violet-400', text: 'Restarting com.android.bluetooth cleanly...' },
        { tag: '[STDOUT]', color: 'text-emerald-300', text: 'SDP Registered: 0x110B (A2DP Sink), 0x110E (AVRCP)' },
        { tag: '[ROUTER]', color: 'text-cyan-300', text: 'A2dpSinkAudioRouter: A2dpSuspended=false, STREAM_MUSIC synced' }
      ];

      const logsSigkill = [
        { tag: '[STAGE:INIT]', color: 'text-cyan-400', text: 'RootBluetoothSdpService -> Requesting su shell...' },
        { tag: '[STAGE:PROPS]', color: 'text-cyan-400', text: 'setprop bluetooth.profile.a2dp.sink.enabled true' },
        { tag: '[STAGE:SDP]', color: 'text-violet-400', text: 'kill -9 $(pidof com.android.bluetooth)' },
        { tag: '[STDERR]', color: 'text-rose-400', text: 'sh: <stdin>[4]: Killed (SIGKILL)' },
        { tag: '[DIAGNOSTIC]', color: 'text-amber-300', text: 'Captured exit code 137 (SIGKILL) in ShellDiagnosticLogHandler' },
        { tag: '[HINT]', color: 'text-slate-400', text: 'Inspect trace in Diagnostics Dialog or retry soft-restart.' }
      ];

      const selected = type === 'success' ? logsSuccess : logsSigkill;
      selected.forEach((line, idx) => {
        setTimeout(() => {
          const div = document.createElement('div');
          div.innerHTML = `<span class="${line.color} font-bold">${line.tag}</span> <span class="text-slate-300">${line.text}</span>`;
          term.appendChild(div);
          term.scrollTop = term.scrollHeight;
        }, idx * 70);
      });

      if (type === 'success') {
        status.className = 'text-emerald-400';
        status.textContent = 'EXIT_CODE: 0 (SUCCESS)';
      } else {
        status.className = 'text-rose-400';
        status.textContent = 'EXIT_CODE: 137 (SIGKILL)';
      }
    }

    let sppPaired = true;
    let sppBle = true;
    function toggleSppOption(opt) {
      if (opt === 'pairing') {
        sppPaired = !sppPaired;
        document.getElementById('sppPairingLabel').textContent = sppPaired ? 'Paired (RFCOMM)' : 'Unpaired (Insecure)';
      } else {
        sppBle = !sppBle;
        document.getElementById('sppBleLabel').textContent = sppBle ? 'Broadcasting ON' : 'Beacon OFF';
      }
    }

    // =========================================================================
    // 2. High-Res AudioTrack & PcmSyncDelayLine Simulator
    // =========================================================================
    let currentSampleRate = 48000;
    let currentBitDepth = 24;
    let currentBufferProfile = 'BALANCED';
    let currentSyncOffsetMs = 0;

    function selectPcmFormat(rate, bits, btnEl) {
      currentSampleRate = rate;
      currentBitDepth = bits;

      document.querySelectorAll('.pcm-fmt-btn').forEach(b => {
        b.className = 'pcm-fmt-btn p-3 rounded-xl border text-left transition bg-slate-900/60 border-slate-800 hover:border-slate-700';
        b.querySelector('div:first-child').className = 'font-mono text-sm font-bold text-white';
        b.querySelector('div:last-child').className = 'font-mono text-xs text-slate-400';
      });
      btnEl.className = 'pcm-fmt-btn p-3 rounded-xl border text-left transition bg-cyan-500/15 border-cyan-500/50';
      btnEl.querySelector('div:first-child').className = 'font-mono text-sm font-bold text-cyan-300';
      btnEl.querySelector('div:last-child').className = 'font-mono text-xs text-cyan-200/80';

      const floatBadge = document.getElementById('pcmFloatBadge');
      if (bits >= 24) {
        floatBadge.classList.remove('hidden');
      } else {
        floatBadge.classList.add('hidden');
      }

      recalcAudioEngine();
      updateSyncOffset(currentSyncOffsetMs);
    }

    function selectBufferProfile(profile, btnEl) {
      currentBufferProfile = profile;
      document.querySelectorAll('.buf-prof-btn').forEach(b => {
        b.className = 'buf-prof-btn p-3 rounded-xl border text-center font-mono text-xs font-semibold transition bg-slate-900/60 border-slate-800 text-slate-300';
      });
      btnEl.className = 'buf-prof-btn p-3 rounded-xl border text-center font-mono text-xs font-semibold transition bg-violet-500/15 border-violet-500/50 text-violet-200';
      recalcAudioEngine();
    }

    function recalcAudioEngine() {
      const channels = 2; // Stereo
      const bytesPerSample = currentBitDepth / 8;
      const frameBytes = channels * bytesPerSample;
      const bitrateKbps = Math.round((currentSampleRate * currentBitDepth * channels) / 1000);

      const lowLat = document.getElementById('chkLowLatency').checked;
      const ultraLow = document.getElementById('chkUltraLowLatency').checked;

      let profileMult = currentBufferProfile === 'LOW_LATENCY' ? 2 : currentBufferProfile === 'BALANCED' ? 4 : 8;
      if (ultraLow) profileMult = 1;
      else if (lowLat && profileMult > 2) profileMult = 2;

      const halBurstFrames = Math.round(currentSampleRate * 0.005); // ~5ms HAL burst
      const bufferFrames = halBurstFrames * profileMult;
      const bufferBytes = ultraLow ? Math.max(512, bufferFrames * frameBytes) : bufferFrames * frameBytes;
      const latencyMs = ((bufferFrames / currentSampleRate) * 1000 + (lowLat ? 4.2 : 14.5)).toFixed(1);

      document.getElementById('calcBitrate').textContent = `${bitrateKbps.toLocaleString()} kbps`;
      document.getElementById('calcFrameBytes').textContent = `${frameBytes} Bytes/fr`;
      document.getElementById('calcBufferBytes').textContent = `${bufferBytes.toLocaleString()} B`;
      document.getElementById('calcLatencyMs').textContent = `${latencyMs} ms`;

      // Sync to Signal Scope telemetry display
      document.getElementById('telThroughput').textContent = `${bitrateKbps.toLocaleString()} kbps`;
      document.getElementById('telLatency').textContent = `${latencyMs} ms`;
    }

    function setSyncOffset(val) {
      document.getElementById('syncOffsetSlider').value = val;
      updateSyncOffset(val);
    }

    function updateSyncOffset(val) {
      const ms = parseInt(val, 10);
      currentSyncOffsetMs = ms;

      const badge = document.getElementById('syncOffsetBadge');
      const box = document.getElementById('syncBehaviorBox');
      const fillBar = document.getElementById('syncBufferFillBar');
      const queuedText = document.getElementById('syncQueuedFrames');

      const frameBytes = 2 * (currentBitDepth / 8);
      const framesDelta = Math.round((Math.abs(ms) / 1000) * currentSampleRate);
      const kbDelta = ((framesDelta * frameBytes) / 1024).toFixed(1);

      // Map -250..+500 to 0..100% bar width
      const pct = Math.round(((ms + 250) / 750) * 100);
      fillBar.style.width = `${pct}%`;

      if (ms > 0) {
        badge.textContent = `+${ms} ms`;
        badge.className = 'px-3 py-1 rounded-xl bg-violet-500/20 text-violet-300 border border-violet-500/40 font-mono text-sm font-bold';
        queuedText.textContent = `+${framesDelta.toLocaleString()} frames (${kbDelta} KB held)`;
        box.innerHTML = `<strong class="text-violet-300 font-mono">+${ms} ms (Delay Playback):</strong> Holds a precise <strong>${ms} ms</strong> (${framesDelta.toLocaleString()} stereo frames / ${kbDelta} KB) backlog in the circular memory ring before draining to <span class="code-pill">AudioTrack.write(...)</span>, syncing audio behind Bluetooth/video lag.`;
      } else if (ms < 0) {
        badge.textContent = `${ms} ms`;
        badge.className = 'px-3 py-1 rounded-xl bg-amber-500/20 text-amber-300 border border-amber-500/40 font-mono text-sm font-bold';
        queuedText.textContent = `-${framesDelta.toLocaleString()} frames (${kbDelta} KB dropped)`;
        box.innerHTML = `<strong class="text-amber-300 font-mono">${ms} ms (Advance Audio):</strong> Drops queued PCM frames proportionally (<strong>${framesDelta.toLocaleString()} frames</strong>) from the ring buffer to pull audio timing forward immediately.`;
      } else {
        badge.textContent = `0 ms`;
        badge.className = 'px-3 py-1 rounded-xl bg-cyan-500/15 text-cyan-300 border border-cyan-500/30 font-mono text-sm font-bold';
        queuedText.textContent = `0 frames (0.0 KB)`;
        box.innerHTML = `<strong class="text-cyan-300 font-mono">0 ms (Pass-Through):</strong> Incoming stereo PCM frames pass directly to <span class="code-pill">AudioTrack.write(...)</span> with zero artificial delay or frame dropping.`;
      }
    }

    // =========================================================================
    // 3. Lock Screen Interactive Controls
    // =========================================================================
    let lockMuted = false;
    let lockStopped = false;
    let showWhenLocked = false;

    function toggleLockMute() {
      lockMuted = !lockMuted;
      const btn = document.getElementById('btnLockMute');
      const badge = document.getElementById('lockMediaStateBadge');
      btn.textContent = lockMuted ? 'Unmute' : 'Mute';
      if (!lockStopped) {
        badge.textContent = lockMuted ? 'MUTED' : 'PLAYING';
        badge.className = lockMuted
          ? 'font-mono text-[10px] px-2 py-0.5 rounded bg-amber-500/20 text-amber-300'
          : 'font-mono text-[10px] px-2 py-0.5 rounded bg-emerald-500/15 text-emerald-300';
      }
    }

    function toggleLockStop() {
      lockStopped = !lockStopped;
      const btn = document.getElementById('btnLockStop');
      const badge = document.getElementById('lockMediaStateBadge');
      btn.textContent = lockStopped ? 'Resume' : 'Stop';
      badge.textContent = lockStopped ? 'STOPPED' : (lockMuted ? 'MUTED' : 'PLAYING');
      badge.className = lockStopped
        ? 'font-mono text-[10px] px-2 py-0.5 rounded bg-rose-500/20 text-rose-300'
        : 'font-mono text-[10px] px-2 py-0.5 rounded bg-emerald-500/15 text-emerald-300';
    }

    function toggleShowWhenLocked() {
      showWhenLocked = !showWhenLocked;
      const overlay = document.getElementById('showWhenLockedConsole');
      const btn = document.getElementById('btnShowWhenLocked');
      const status = document.getElementById('lockWindowStatus');
      if (showWhenLocked) {
        overlay.classList.remove('hidden');
        btn.textContent = 'Disable Activity Overlay';
        status.textContent = 'setShowWhenLocked(true)';
      } else {
        overlay.classList.add('hidden');
        btn.textContent = 'Enable Activity Overlay';
        status.textContent = 'VISIBILITY_PUBLIC';
      }
    }

    // =========================================================================
    // 4. Signal Scope Canvas Visualizer (8 Modes + Optional Web Audio Synth)
    // =========================================================================
    const SCOPE_MODES = [
      'Dual Bars',
      'Narrow Quads',
      'Neon Wave',
      'Stereo Wave',
      'Radial Pulse',
      'Aurora',
      'Floor VU',
      'Matrix Dots'
    ];

    let activeScopeMode = 'Dual Bars';
    let scopeConnState = 'Connected';
    let scopeMuted = false;
    const bands = new Float32Array(28);
    let leftRms = 0.5;
    let rightRms = 0.48;

    // Optional Web Audio Synth Generator
    let audioCtx = null;
    let analyserNode = null;
    let synthPlaying = false;
    let synthTimer = null;

    function toggleWebAudioSynth() {
      const btnLabel = document.getElementById('synthBtnLabel');
      if (!synthPlaying) {
        try {
          if (!audioCtx) {
            audioCtx = new (window.AudioContext || window.webkitAudioContext)();
            analyserNode = audioCtx.createAnalyser();
            analyserNode.fftSize = 128;
            analyserNode.connect(audioCtx.destination);
          }
          if (audioCtx.state === 'suspended') audioCtx.resume();
          synthPlaying = true;
          btnLabel.textContent = 'Audio Synth: ON (Live DSP)';
          startSynthLoop();
        } catch (e) {
          btnLabel.textContent = 'Audio Blocked (Visual Sim)';
        }
      } else {
        synthPlaying = false;
        clearInterval(synthTimer);
        btnLabel.textContent = 'Audio Synth: OFF (Visual Sim)';
      }
    }

    function startSynthLoop() {
      const notes = [110, 130.81, 146.83, 164.81, 196.0, 220.0, 261.63];
      let step = 0;
      synthTimer = setInterval(() => {
        if (!synthPlaying || !audioCtx || scopeMuted || scopeConnState !== 'Connected') return;
        const now = audioCtx.currentTime;
        const osc = audioCtx.createOscillator();
        const gain = audioCtx.createGain();
        osc.type = step % 4 === 0 ? 'triangle' : 'sine';
        osc.frequency.setValueAtTime(notes[step % notes.length] * (step % 3 === 0 ? 2 : 1), now);

        gain.gain.setValueAtTime(0.001, now);
        gain.gain.exponentialRampToValueAtTime(0.08, now + 0.02);
        gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.22);

        osc.connect(gain);
        gain.connect(analyserNode);
        osc.start(now);
        osc.stop(now + 0.24);
        step++;
      }, 240);
    }

    function initScopeButtons() {
      const container = document.getElementById('scopeModeButtons');
      SCOPE_MODES.forEach(mode => {
        const btn = document.createElement('button');
        btn.textContent = mode;
        btn.className = mode === activeScopeMode
          ? 'scope-mode-btn px-2.5 py-2 rounded-xl font-mono text-xs font-semibold bg-cyan-500/20 text-cyan-300 border border-cyan-500/50 transition truncate'
          : 'scope-mode-btn px-2.5 py-2 rounded-xl font-mono text-xs font-medium bg-slate-900/80 text-slate-400 border border-slate-800 hover:text-white hover:border-slate-700 transition truncate';
        btn.onclick = () => {
          activeScopeMode = mode;
          document.querySelectorAll('.scope-mode-btn').forEach(b => {
            b.className = 'scope-mode-btn px-2.5 py-2 rounded-xl font-mono text-xs font-medium bg-slate-900/80 text-slate-400 border border-slate-800 hover:text-white hover:border-slate-700 transition truncate';
          });
          btn.className = 'scope-mode-btn px-2.5 py-2 rounded-xl font-mono text-xs font-semibold bg-cyan-500/20 text-cyan-300 border border-cyan-500/50 transition truncate';
        };
        container.appendChild(btn);
      });
    }

    function setScopeState(state) {
      scopeConnState = state;
      const dot = document.getElementById('scopeConnDot');
      const label = document.getElementById('scopeConnLabel');
      label.textContent = state.toUpperCase();

      if (state === 'Connected') {
        dot.className = 'w-2.5 h-2.5 rounded-full bg-emerald-400';
        label.className = 'text-emerald-400';
      } else if (state === 'Listening') {
        dot.className = 'w-2.5 h-2.5 rounded-full bg-cyan-400 animate-ping';
        label.className = 'text-cyan-400';
      } else if (state === 'Reconnecting') {
        dot.className = 'w-2.5 h-2.5 rounded-full bg-amber-400 animate-pulse';
        label.className = 'text-amber-400';
      } else {
        dot.className = 'w-2.5 h-2.5 rounded-full bg-slate-500';
        label.className = 'text-slate-400';
      }
    }

    function toggleScopeMute() {
      scopeMuted = !scopeMuted;
      const badge = document.getElementById('scopeMuteBadge');
      if (scopeMuted) badge.classList.remove('hidden');
      else badge.classList.add('hidden');
    }

    // Resize responsive canvases properly
    function resizeCanvasToDisplaySize(canvas) {
      const rect = canvas.parentElement.getBoundingClientRect();
      const dpr = window.devicePixelRatio || 1;
      const width = Math.max(1, Math.floor(rect.width * dpr));
      const height = Math.max(1, Math.floor(rect.height * dpr));
      if (canvas.width !== width || canvas.height !== height) {
        canvas.width = width;
        canvas.height = height;
      }
      return { w: width, h: height, dpr };
    }

    function updateTelemetryData(t) {
      let energyMult = 1.0;
      if (scopeMuted || scopeConnState === 'Idle') energyMult = 0.04;
      else if (scopeConnState === 'Listening') energyMult = 0.18;
      else if (scopeConnState === 'Reconnecting') energyMult = 0.28;

      if (synthPlaying && analyserNode && energyMult > 0.5) {
        const freqData = new Uint8Array(analyserNode.frequencyBinCount);
        analyserNode.getByteFrequencyData(freqData);
        for (let i = 0; i < 28; i++) {
          const idx = Math.min(freqData.length - 1, Math.floor(i * 1.4));
          const target = (freqData[idx] / 255) * 0.95 + 0.05;
          bands[i] += (target - bands[i]) * 0.35;
        }
      } else {
        for (let i = 0; i < 28; i++) {
          const bassBoost = Math.exp(-i * 0.09);
          const wave = Math.sin(t * 4.2 + i * 0.45) * 0.5 + 0.5;
          const beat = Math.pow(Math.sin(t * 2.6) * 0.5 + 0.5, 3);
          const raw = (0.18 + 0.52 * wave * bassBoost + 0.3 * beat * bassBoost) * energyMult;
          bands[i] += (raw - bands[i]) * 0.25;
        }
      }

      leftRms = Math.min(1, bands.slice(0, 14).reduce((a, b) => a + b, 0) / 11);
      rightRms = Math.min(1, bands.slice(2, 18).reduce((a, b) => a + b, 0) / 12);

      // Update DOM meters
      document.getElementById('vuBarLeft').style.width = `${Math.round(leftRms * 100)}%`;
      document.getElementById('vuBarRight').style.width = `${Math.round(rightRms * 100)}%`;
      const dbL = leftRms < 0.02 ? '-∞' : (20 * Math.log10(leftRms)).toFixed(1);
      const dbR = rightRms < 0.02 ? '-∞' : (20 * Math.log10(rightRms)).toFixed(1);
      document.getElementById('vuDbLeft').textContent = `${dbL} dB`;
      document.getElementById('vuDbRight').textContent = `${dbR} dB`;

      const fillPct = scopeConnState === 'Connected' ? Math.round(62 + Math.sin(t * 2) * 8) : 0;
      document.getElementById('telBufferFill').textContent = `${fillPct}%`;
    }

    function drawHeroMiniCanvas(canvas, t) {
      const { w, h, dpr } = resizeCanvasToDisplaySize(canvas);
      const ctx = canvas.getContext('2d');
      ctx.clearRect(0, 0, w, h);

      const barCount = 56;
      const gap = 3 * dpr;
      const barW = Math.max(2 * dpr, (w - gap * (barCount - 1)) / barCount);

      for (let i = 0; i < barCount; i++) {
        const bandVal = bands[i % 28];
        const barH = Math.max(4 * dpr, bandVal * h * 0.85);
        const x = i * (barW + gap);
        const y = (h - barH) / 2;

        const grad = ctx.createLinearGradient(0, y, 0, y + barH);
        grad.addColorStop(0, '#00f0ff');
        grad.addColorStop(1, '#9d4edd');
        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.roundRect(x, y, barW, barH, 3 * dpr);
        ctx.fill();
      }
    }

    function drawSignalScope(canvas, t) {
      const { w, h, dpr } = resizeCanvasToDisplaySize(canvas);
      const ctx = canvas.getContext('2d');
      ctx.clearRect(0, 0, w, h);

      // Subtle oscilloscope grid
      ctx.strokeStyle = 'rgba(148, 163, 184, 0.07)';
      ctx.lineWidth = 1 * dpr;
      const step = 32 * dpr;
      for (let x = 0; x < w; x += step) {
        ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, h); ctx.stroke();
      }
      for (let y = 0; y < h; y += step) {
        ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke();
      }

      const pad = 20 * dpr;
      const innerW = w - pad * 2;
      const innerH = h - pad * 2;

      if (activeScopeMode === 'Dual Bars') {
        const count = 28;
        const gap = 6 * dpr;
        const bw = (innerW - gap * (count - 1)) / count;
        for (let i = 0; i < count; i++) {
          const val = bands[i];
          const bh = Math.max(6 * dpr, val * innerH * 0.46);
          const x = pad + i * (bw + gap);
          const midY = h / 2;

          ctx.fillStyle = '#00f0ff';
          ctx.beginPath();
          ctx.roundRect(x, midY - bh - 2 * dpr, bw, bh, 4 * dpr);
          ctx.fill();

          ctx.fillStyle = '#9d4edd';
          ctx.beginPath();
          ctx.roundRect(x, midY + 2 * dpr, bw, bh * 0.9, 4 * dpr);
          ctx.fill();
        }
      } else if (activeScopeMode === 'Narrow Quads') {
        const count = 28;
        const gap = 8 * dpr;
        const bw = (innerW - gap * (count - 1)) / count;
        const segH = 8 * dpr;
        const segGap = 4 * dpr;
        for (let i = 0; i < count; i++) {
          const maxSegs = Math.floor(innerH / (segH + segGap));
          const activeSegs = Math.max(1, Math.round(bands[i] * maxSegs));
          const x = pad + i * (bw + gap);
          for (let s = 0; s < activeSegs; s++) {
            const y = h - pad - (s + 1) * (segH + segGap);
            ctx.fillStyle = s > maxSegs * 0.75 ? '#f43f5e' : s > maxSegs * 0.45 ? '#00f0ff' : '#10b981';
            ctx.beginPath();
            ctx.roundRect(x, y, bw, segH, 2 * dpr);
            ctx.fill();
          }
        }
      } else if (activeScopeMode === 'Neon Wave') {
        ctx.save();
        ctx.strokeStyle = '#00f0ff';
        ctx.lineWidth = 3 * dpr;
        ctx.shadowColor = '#00f0ff';
        ctx.shadowBlur = 14 * dpr;
        ctx.beginPath();
        for (let i = 0; i <= 100; i++) {
          const ratio = i / 100;
          const x = pad + ratio * innerW;
          const bIdx = Math.min(27, Math.floor(ratio * 28));
          const amp = bands[bIdx] * (innerH * 0.42);
          const y = h / 2 + Math.sin(ratio * 14 + t * 6) * amp;
          if (i === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        }
        ctx.stroke();
        ctx.restore();
      } else if (activeScopeMode === 'Stereo Wave') {
        // Left Cyan + Right Violet overlapping waveforms
        [
          { color: '#00f0ff', phase: 0, mult: leftRms },
          { color: '#9d4edd', phase: 1.4, mult: rightRms }
        ].forEach(ch => {
          ctx.save();
          ctx.strokeStyle = ch.color;
          ctx.lineWidth = 2.5 * dpr;
          ctx.beginPath();
          for (let i = 0; i <= 90; i++) {
            const r = i / 90;
            const x = pad + r * innerW;
            const env = Math.sin(r * Math.PI);
            const y = h / 2 + Math.sin(r * 16 + t * 5 + ch.phase) * env * ch.mult * innerH * 0.45;
            if (i === 0) ctx.moveTo(x, y);
            else ctx.lineTo(x, y);
          }
          ctx.stroke();
          ctx.restore();
        });
      } else if (activeScopeMode === 'Radial Pulse') {
        const cx = w / 2;
        const cy = h / 2;
        const baseR = Math.min(innerW, innerH) * 0.22;
        const count = 28;
        for (let i = 0; i < count; i++) {
          const angle = (i / count) * Math.PI * 2 - Math.PI / 2;
          const len = baseR + bands[i] * Math.min(innerW, innerH) * 0.24;
          const x1 = cx + Math.cos(angle) * baseR;
          const y1 = cy + Math.sin(angle) * baseR;
          const x2 = cx + Math.cos(angle) * len;
          const y2 = cy + Math.sin(angle) * len;

          ctx.strokeStyle = i % 2 === 0 ? '#00f0ff' : '#9d4edd';
          ctx.lineWidth = 4 * dpr;
          ctx.lineCap = 'round';
          ctx.beginPath();
          ctx.moveTo(x1, y1);
          ctx.lineTo(x2, y2);
          ctx.stroke();
        }
      } else if (activeScopeMode === 'Aurora') {
        const grad = ctx.createLinearGradient(pad, 0, w - pad, 0);
        grad.addColorStop(0, 'rgba(16, 185, 129, 0.35)');
        grad.addColorStop(0.5, 'rgba(0, 240, 255, 0.45)');
        grad.addColorStop(1, 'rgba(157, 78, 221, 0.35)');
        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.moveTo(pad, h - pad);
        for (let i = 0; i < 28; i++) {
          const x = pad + (i / 27) * innerW;
          const y = h - pad - bands[i] * innerH * 0.85;
          ctx.lineTo(x, y);
        }
        ctx.lineTo(w - pad, h - pad);
        ctx.closePath();
        ctx.fill();
      } else if (activeScopeMode === 'Floor VU') {
        const count = 28;
        const gap = 5 * dpr;
        const bw = (innerW - gap * (count - 1)) / count;
        for (let i = 0; i < count; i++) {
          const bh = Math.max(4 * dpr, bands[i] * innerH * 0.9);
          const x = pad + i * (bw + gap);
          const y = h - pad - bh;
          const grad = ctx.createLinearGradient(0, h - pad, 0, pad);
          grad.addColorStop(0, '#10b981');
          grad.addColorStop(0.6, '#00f0ff');
          grad.addColorStop(1, '#f59e0b');
          ctx.fillStyle = grad;
          ctx.beginPath();
          ctx.roundRect(x, y, bw, bh, [4 * dpr, 4 * dpr, 0, 0]);
          ctx.fill();
        }
      } else if (activeScopeMode === 'Matrix Dots') {
        const cols = 28;
        const rows = 12;
        const cellW = innerW / cols;
        const cellH = innerH / rows;
        const radius = Math.min(cellW, cellH) * 0.28;
        for (let c = 0; c < cols; c++) {
          const activeRows = Math.round(bands[c] * rows);
          for (let r = 0; r < rows; r++) {
            const isLit = (rows - 1 - r) < activeRows;
            ctx.fillStyle = isLit ? (r < 3 ? '#9d4edd' : '#00f0ff') : 'rgba(51, 65, 85, 0.25)';
            ctx.beginPath();
            ctx.arc(pad + c * cellW + cellW / 2, pad + r * cellH + cellH / 2, radius, 0, Math.PI * 2);
            ctx.fill();
          }
        }
      }
    }

    function animateLoop(timestamp) {
      const t = timestamp * 0.001;
      updateTelemetryData(t);

      const heroCanvas = document.getElementById('heroMiniCanvas');
      const scopeCanvas = document.getElementById('signalScopeCanvas');
      if (heroCanvas) drawHeroMiniCanvas(heroCanvas, t);
      if (scopeCanvas) drawSignalScope(scopeCanvas, t);

      requestAnimationFrame(animateLoop);
    }

    // Boot everything cleanly on window load
    window.onload = function () {
      runShellSim('success');
      recalcAudioEngine();
      updateSyncOffset(0);
      initScopeButtons();
      requestAnimationFrame(animateLoop);
    };
  </script>
</body>
</html>
