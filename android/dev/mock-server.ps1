<#
Copyright 2015-2026 Alexander Barthel and the Little Navmap contributors.
SPDX-License-Identifier: GPL-3.0-or-later

Modified for the unofficial Little Navmap Android client in 2026.
This development-only mock is not part of an official Little Navmap release.
#>

[CmdletBinding()]
param(
    [Parameter()]
    [ValidateRange(1, 65535)]
    [int] $Port = 8965,

    [Parameter()]
    [string] $WebRoot,

    [Parameter()]
    [ValidateSet('Active', 'Inactive', 'HttpError', 'Malformed')]
    [string] $SimState = 'Active',

    [Parameter()]
    [ValidateSet('Found', 'NotFound', 'HttpError', 'Malformed')]
    [string] $AirportState = 'Found'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$resolvedWebRoot = $null
if (-not [string]::IsNullOrWhiteSpace($WebRoot)) {
    $webRootItem = Get-Item -LiteralPath $WebRoot -ErrorAction Stop
    if (-not $webRootItem.PSIsContainer -or $webRootItem.PSProvider.Name -ne 'FileSystem') {
        throw "WebRoot must be a filesystem directory: $WebRoot"
    }

    $resolvedWebRoot = [System.IO.Path]::GetFullPath($webRootItem.FullName)
    if ($resolvedWebRoot -ne [System.IO.Path]::GetPathRoot($resolvedWebRoot)) {
        $resolvedWebRoot = $resolvedWebRoot.TrimEnd(
            [System.IO.Path]::DirectorySeparatorChar,
            [System.IO.Path]::AltDirectorySeparatorChar
        )
    }
    if (-not (Test-Path -LiteralPath (Join-Path $resolvedWebRoot 'index.html') -PathType Leaf)) {
        throw "WebRoot does not contain index.html: $resolvedWebRoot"
    }
}

$rootPage = @'
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
  <title>Little Navmap Mobile Mock</title>
  <style>
    html,body,#webFrontend{display:block;width:100%;height:100%;margin:0;border:0;background:#eef3f2}
  </style>
</head>
<body>
  <iframe id="webFrontend" src="/frontend.html" title="Little Navmap web frontend"></iframe>
</body>
</html>
'@

$frontendPage = @'
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
  <title>Little Navmap</title>
  <style>
    :root{color-scheme:light;--nav:#14242b;--nav2:#20353d;--active:#ffca58;--line:#d2ddda}
    *{box-sizing:border-box}
    html,body{width:100%;height:100%;margin:0;overflow:hidden;background:#eef3f2;font-family:system-ui,-apple-system,"Segoe UI",sans-serif}
    #appShell{display:grid;grid-template-columns:88px minmax(0,1fr);height:100%}
    #menubarContainer{display:flex;flex-direction:column;gap:4px;padding:max(12px,env(safe-area-inset-top)) 8px max(12px,env(safe-area-inset-bottom));background:var(--nav);color:white}
    #menubarContainer button{min-height:62px;border:0;border-radius:6px;background:transparent;color:#d7e2e4;font:600 11px/1.2 system-ui;letter-spacing:0;cursor:pointer}
    #menubarContainer button::before{display:block;margin-bottom:6px;color:#9eb2b7;font:700 20px/1 ui-monospace,monospace}
    #buttonMap::before{content:"MAP"}#buttonFlightPlan::before{content:"FPL"}#buttonAircraft::before{content:"AC"}#buttonProgress::before{content:"ETE"}#buttonAirport::before{content:"APT"}
    #menubarContainer button.active{background:var(--nav2);color:var(--active);box-shadow:inset 3px 0 0 var(--active)}
    #mainContainer{display:grid;min-width:0;min-height:0;background:#eef3f2}
    #contentiframe{display:block;width:100%;height:100%;border:0;grid-area:1/1}
    #addoniframe{display:none;width:100%;height:100%;border:0;grid-area:1/1}
    @media(max-width:700px){
      #appShell{grid-template-columns:1fr;grid-template-rows:minmax(0,1fr) 68px}
      #mainContainer{grid-row:1}
      #menubarContainer{grid-row:2;flex-direction:row;gap:2px;padding:5px max(5px,env(safe-area-inset-right)) max(5px,env(safe-area-inset-bottom)) max(5px,env(safe-area-inset-left));order:2}
      #menubarContainer button{flex:1;min-width:0;min-height:52px;padding:3px 1px;font-size:9px}
      #menubarContainer button::before{margin-bottom:2px;font-size:14px}
      #menubarContainer button.active{box-shadow:inset 0 3px 0 var(--active)}
    }
  </style>
</head>
<body>
  <div id="appShell">
    <nav id="menubarContainer" aria-label="Views">
      <button id="buttonMap" class="active" type="button" data-page="/map.html">Map</button>
      <button id="buttonFlightPlan" type="button" data-page="/flightplan.html">Plan</button>
      <button id="buttonAircraft" type="button" data-page="/aircraft.html">Aircraft</button>
      <button id="buttonProgress" type="button" data-page="/progress.html">Progress</button>
      <button id="buttonAirport" type="button" data-page="/airport.html">Airport</button>
    </nav>
    <main id="mainContainer" data-menubarsplacement="left">
      <iframe id="contentiframe" src="/map.html" title="Selected Little Navmap view"></iframe>
      <iframe id="addoniframe" src="/status.html" title="Little Navmap add-on view" hidden></iframe>
    </main>
  </div>
  <script>
    (function () {
      'use strict';
      var content = document.getElementById('contentiframe');
      var buttons = document.querySelectorAll('#menubarContainer button[data-page]');
      function select(button) {
        for (var index = 0; index < buttons.length; index += 1) {
          var active = buttons[index] === button;
          buttons[index].classList.toggle('active', active);
          buttons[index].setAttribute('aria-pressed', active ? 'true' : 'false');
        }
        content.src = button.getAttribute('data-page');
      }
      for (var index = 0; index < buttons.length; index += 1) {
        buttons[index].addEventListener('click', function (event) { select(event.currentTarget); });
      }
    }());
  </script>
</body>
</html>
'@

$mapPage = @'
<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Map</title><style>
*{box-sizing:border-box}html,body{height:100%;margin:0;font-family:system-ui,-apple-system,"Segoe UI",sans-serif;color:#16262b;background:#bfd4d4}body{overflow:hidden}
.map{position:relative;width:100%;height:100%;background-color:#b9d7dc;background-image:linear-gradient(rgba(31,84,91,.12) 1px,transparent 1px),linear-gradient(90deg,rgba(31,84,91,.12) 1px,transparent 1px);background-size:44px 44px}
.land{position:absolute;background:#dce3bd;border:2px solid #879b7b;box-shadow:0 2px 8px rgba(24,53,57,.18)}.west{inset:-10% 61% -8% -15%;border-radius:0 44% 38% 0}.east{inset:-9% -16% -8% 66%;border-radius:42% 0 0 35%}
.route{position:absolute;inset:0;width:100%;height:100%}.route path{fill:none;stroke:#b92d42;stroke-width:6;stroke-linecap:round;stroke-linejoin:round}.route .halo{stroke:white;stroke-width:11;opacity:.75}
.airport{position:absolute;transform:translate(-50%,-50%);min-width:58px;padding:5px 7px;border:1px solid #23383e;border-radius:4px;background:#fff;color:#1a3036;font:700 12px/1.1 ui-monospace,monospace;text-align:center;box-shadow:0 2px 5px rgba(0,0,0,.22)}.airport small{display:block;color:#5e7074;font-size:9px;margin-top:3px}.cyvr{left:21%;top:70%}.ksea{left:47%;top:50%}.kpd{left:76%;top:28%}
.plane{position:absolute;left:57%;top:42%;transform:translate(-50%,-50%) rotate(31deg);width:38px;height:38px;border-radius:50%;background:#14242b;color:#ffca58;display:grid;place-items:center;font:800 21px/1 sans-serif;box-shadow:0 0 0 4px rgba(255,255,255,.85)}
.topbar{position:absolute;top:max(10px,env(safe-area-inset-top));left:10px;right:10px;display:flex;justify-content:space-between;gap:8px}.chip{padding:8px 10px;border:1px solid rgba(20,36,43,.3);border-radius:5px;background:rgba(255,255,255,.94);font:700 12px/1.1 ui-monospace,monospace;box-shadow:0 2px 7px rgba(0,0,0,.16)}
.scale{position:absolute;right:14px;bottom:max(14px,env(safe-area-inset-bottom));padding:5px 8px;border-bottom:3px solid #182d33;background:rgba(255,255,255,.85);font:700 11px ui-monospace,monospace}
@media(max-width:520px){.chip:last-child{display:none}.airport{min-width:48px;font-size:10px}.route path{stroke-width:5}.route .halo{stroke-width:9}}
</style></head><body><div class="map"><div class="land west"></div><div class="land east"></div>
<svg class="route" viewBox="0 0 1000 700" preserveAspectRatio="none" aria-label="Route from CYVR to KPDX"><path class="halo" d="M210 490 C330 420 385 375 470 350 S650 250 760 196"/><path d="M210 490 C330 420 385 375 470 350 S650 250 760 196"/></svg>
<div class="airport cyvr">CYVR<small>08R / 26L</small></div><div class="airport ksea">KSEA<small>16L / 34R</small></div><div class="airport kpd">KPDX<small>10L / 28R</small></div><div class="plane">+</div>
<div class="topbar"><div class="chip">N47 53.2&nbsp; W122 41.4</div><div class="chip">TRK 164&deg;&nbsp;&nbsp; GS 126 kt</div></div><div class="scale">25 NM</div></div></body></html>
'@

$flightPlanPage = @'
<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Flight Plan</title><style>
*{box-sizing:border-box}html,body{min-height:100%;margin:0;background:#f5f7f5;color:#18292e;font-family:system-ui,-apple-system,"Segoe UI",sans-serif}header{padding:max(18px,env(safe-area-inset-top)) 20px 16px;background:#172a31;color:#fff}h1{margin:0 0 6px;font-size:22px;letter-spacing:0}header p{margin:0;color:#b9c9cc;font:600 13px ui-monospace,monospace}.summary{display:grid;grid-template-columns:repeat(4,1fr);border-bottom:1px solid #ccd8d5;background:#fff}.metric{padding:14px 16px;border-right:1px solid #dce5e2}.metric:last-child{border:0}.metric span{display:block;color:#67787c;font-size:11px;text-transform:uppercase}.metric b{display:block;margin-top:4px;font:700 17px ui-monospace,monospace}.table{padding:12px 16px 24px}.row{display:grid;grid-template-columns:34px minmax(72px,1.1fr) minmax(90px,1.7fr) 70px 70px;align-items:center;min-height:55px;border-bottom:1px solid #d8e1df}.row.head{min-height:34px;color:#718084;font-size:10px;text-transform:uppercase}.seq{width:25px;height:25px;border-radius:50%;display:grid;place-items:center;background:#dce7e4;font:700 11px ui-monospace,monospace}.row.active{margin:0 -8px;padding:0 8px;border-radius:5px;background:#fff0c9;border-bottom-color:#e0c16f}.ident{font:800 14px ui-monospace,monospace}.name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#526368;font-size:13px}.num{text-align:right;font:650 12px ui-monospace,monospace}@media(max-width:580px){.summary{grid-template-columns:repeat(2,1fr)}.metric:nth-child(2){border-right:0}.row{grid-template-columns:30px 76px minmax(68px,1fr) 58px}.row>*:nth-child(5){display:none}.table{padding:8px}.name{font-size:11px}.metric{padding:11px 12px}.metric b{font-size:15px}}
</style></head><body><header><h1>CYVR to KPDX</h1><p>IFR &middot; C172 &middot; 205 NM</p></header><section class="summary"><div class="metric"><span>ETE</span><b>01:38</b></div><div class="metric"><span>Fuel</span><b>16.4 gal</b></div><div class="metric"><span>Cruise</span><b>8,500 ft</b></div><div class="metric"><span>Wind</span><b>224 / 18</b></div></section><main class="table"><div class="row head"><span></span><span>Ident</span><span>Name / airway</span><span class="num">Dist</span><span class="num">Course</span></div><div class="row"><span class="seq">1</span><span class="ident">CYVR</span><span class="name">Vancouver Intl</span><span class="num">0 NM</span><span class="num">126&deg;</span></div><div class="row"><span class="seq">2</span><span class="ident">DUNCN</span><span class="name">SID DUNCN SIX</span><span class="num">31 NM</span><span class="num">173&deg;</span></div><div class="row active"><span class="seq">3</span><span class="ident">PAE</span><span class="name">V23 Paine VOR</span><span class="num">72 NM</span><span class="num">165&deg;</span></div><div class="row"><span class="seq">4</span><span class="ident">OLM</span><span class="name">V165 Olympia VOR</span><span class="num">58 NM</span><span class="num">183&deg;</span></div><div class="row"><span class="seq">5</span><span class="ident">KPDX</span><span class="name">Portland Intl &middot; ILS 10L</span><span class="num">44 NM</span><span class="num">147&deg;</span></div></main></body></html>
'@

$aircraftPage = @'
<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Aircraft</title><style>
*{box-sizing:border-box}html,body{min-height:100%;margin:0;background:#102127;color:#edf4f3;font-family:system-ui,-apple-system,"Segoe UI",sans-serif}header{display:flex;justify-content:space-between;align-items:end;padding:max(18px,env(safe-area-inset-top)) 18px 14px;border-bottom:1px solid #355059}h1{margin:0;font-size:21px;letter-spacing:0}.live{color:#7bd8a9;font:700 12px ui-monospace,monospace}.grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:1px;background:#355059}.gauge{min-height:150px;padding:20px 16px;background:#152a31;text-align:center}.gauge span{display:block;color:#9db2b6;font-size:11px;text-transform:uppercase}.gauge strong{display:block;margin:18px 0 6px;color:#fff;font:700 clamp(28px,6vw,48px)/1 ui-monospace,monospace}.gauge em{font-style:normal;color:#b7c7ca;font:600 12px ui-monospace,monospace}.bar{height:6px;margin-top:19px;border-radius:3px;background:#2e454d;overflow:hidden}.bar i{display:block;height:100%;background:#f2c451}.attitude{position:relative;height:150px;overflow:hidden;background:linear-gradient(#477c9b 0 47%,#a27047 47% 100%)}.attitude:after{content:"";position:absolute;left:20%;right:20%;top:50%;border-top:3px solid white}.attitude b{position:absolute;z-index:1;inset:0;display:grid;place-items:center;color:white;font:800 20px ui-monospace,monospace;text-shadow:0 1px 2px #000}.strip{display:grid;grid-template-columns:repeat(4,1fr);gap:1px;background:#355059;border-top:1px solid #355059}.strip div{padding:15px;background:#13272e}.strip span{display:block;color:#91a9ae;font-size:10px;text-transform:uppercase}.strip b{display:block;margin-top:5px;font:700 14px ui-monospace,monospace}@media(max-width:620px){.grid{grid-template-columns:repeat(2,1fr)}.gauge{min-height:126px;padding:16px 10px}.gauge strong{margin:13px 0 4px;font-size:29px}.strip{grid-template-columns:repeat(2,1fr)}}
</style></head><body><header><div><h1>Cessna 172S</h1><div class="live">N172LN &middot; SIM CONNECTED</div></div><div class="live">12:43:18Z</div></header><main class="grid"><section class="gauge"><span>Indicated airspeed</span><strong>121</strong><em>KT</em><div class="bar"><i style="width:68%"></i></div></section><section class="gauge"><span>Altitude</span><strong>7,840</strong><em>FT</em><div class="bar"><i style="width:78%"></i></div></section><section class="attitude"><b>+2.1&deg;&nbsp; / &nbsp;1.4&deg;R</b></section><section class="gauge"><span>Vertical speed</span><strong>+310</strong><em>FT/MIN</em><div class="bar"><i style="width:56%"></i></div></section><section class="gauge"><span>Ground speed</span><strong>126</strong><em>KT</em><div class="bar"><i style="width:71%"></i></div></section><section class="gauge"><span>True heading</span><strong>164</strong><em>DEG</em><div class="bar"><i style="width:46%"></i></div></section></main><footer class="strip"><div><span>Fuel</span><b>31.8 GAL</b></div><div><span>Range</span><b>386 NM</b></div><div><span>OAT</span><b>+03 C</b></div><div><span>Pressure</span><b>29.92 IN</b></div></footer></body></html>
'@

$progressPage = @'
<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Progress</title><style>
*{box-sizing:border-box}html,body{min-height:100%;margin:0;background:#edf2f0;color:#17292f;font-family:system-ui,-apple-system,"Segoe UI",sans-serif}.wrap{max-width:920px;margin:auto;padding:max(20px,env(safe-area-inset-top)) 20px max(24px,env(safe-area-inset-bottom))}header{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}h1{margin:0;font-size:23px;letter-spacing:0}.clock{font:700 14px ui-monospace,monospace;color:#52676d}.track{position:relative;height:12px;margin:35px 10px 42px;border-radius:6px;background:#c4d1ce}.track .done{width:58%;height:100%;border-radius:6px;background:#1c7a72}.track i{position:absolute;left:58%;top:50%;width:24px;height:24px;transform:translate(-50%,-50%);border:5px solid white;border-radius:50%;background:#d14654;box-shadow:0 1px 5px #536}.ends{display:flex;justify-content:space-between;margin-top:10px;font:800 13px ui-monospace,monospace}.cards{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.card{min-height:105px;padding:16px;border:1px solid #cad7d4;border-radius:6px;background:#fff}.card span{display:block;color:#677b80;font-size:11px;text-transform:uppercase}.card b{display:block;margin-top:14px;font:750 23px ui-monospace,monospace}.card small{display:block;margin-top:4px;color:#73868a}.next{margin-top:12px;padding:17px 18px;border-left:5px solid #d9a524;background:#fff8df}.next span{color:#765d20;font-size:11px;text-transform:uppercase}.next b{display:block;margin-top:5px;font:750 17px ui-monospace,monospace}.log{margin-top:20px;border-top:1px solid #c8d5d2}.event{display:grid;grid-template-columns:72px 1fr auto;gap:10px;padding:13px 4px;border-bottom:1px solid #d3ddda;font-size:13px}.event time,.event em{font:600 12px ui-monospace,monospace}.event em{font-style:normal;color:#64777c}@media(max-width:600px){.wrap{padding-left:12px;padding-right:12px}.cards{grid-template-columns:repeat(2,1fr)}.card:last-child{grid-column:1/-1}.card{min-height:92px;padding:13px}.card b{font-size:20px}.event{grid-template-columns:62px 1fr}.event em{display:none}}
</style></head><body><main class="wrap"><header><h1>Flight progress</h1><div class="clock">12:43:18Z</div></header><section><div class="track"><div class="done"></div><i></i><div class="ends"><span>CYVR</span><span>KPDX</span></div></div></section><section class="cards"><div class="card"><span>Distance flown</span><b>119 NM</b><small>58 percent</small></div><div class="card"><span>Distance remaining</span><b>86 NM</b><small>to KPDX</small></div><div class="card"><span>Estimated arrival</span><b>13:24Z</b><small>41 minutes</small></div><div class="card"><span>Cross track</span><b>0.3 NM L</b><small>correct 2 degrees</small></div><div class="card"><span>Fuel at destination</span><b>21.6 GAL</b><small>03:06 endurance</small></div><div class="card"><span>Top of descent</span><b>47 NM</b><small>at 13:02Z</small></div></section><div class="next"><span>Next waypoint</span><b>OLM &middot; 28 NM &middot; 13:02Z</b></div><section class="log"><div class="event"><time>12:31Z</time><span>Passed PAE</span><em>8,500 FT</em></div><div class="event"><time>12:18Z</time><span>Reached cruise altitude</span><em>124 KT</em></div><div class="event"><time>11:54Z</time><span>Departed CYVR runway 08R</span><em>IFR</em></div></section></main></body></html>
'@

$airportPage = @'
<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Airport</title><style>
*{box-sizing:border-box}html,body{min-height:100%;margin:0;background:#f4f6f3;color:#17292e;font-family:system-ui,-apple-system,"Segoe UI",sans-serif}header{padding:max(20px,env(safe-area-inset-top)) 20px 18px;background:#263c43;color:white}h1{margin:0;font:800 29px ui-monospace,monospace;letter-spacing:0}header p{margin:4px 0 0;color:#c9d5d6;font-size:14px}.badges{display:flex;flex-wrap:wrap;gap:6px;margin-top:13px}.badge{padding:5px 8px;border-radius:4px;background:#38545d;font:650 11px ui-monospace,monospace}.layout{display:grid;grid-template-columns:minmax(0,1.4fr) minmax(260px,.8fr);gap:14px;padding:14px}.panel{padding:16px;border:1px solid #ced9d6;border-radius:6px;background:white}h2{margin:0 0 13px;font-size:14px;letter-spacing:0;text-transform:uppercase;color:#52686d}.metar{padding:13px;border-left:4px solid #2f8a75;background:#e8f4ee;font:650 13px/1.55 ui-monospace,monospace;overflow-wrap:anywhere}.runway{display:grid;grid-template-columns:64px 1fr 68px;align-items:center;gap:10px;padding:12px 0;border-bottom:1px solid #dbe3e1}.runway:last-child{border:0}.runway b,.runway span:last-child{font:700 13px ui-monospace,monospace}.rwybar{height:9px;background:#4b5b5f;border:2px solid #aab8b8}.facts{display:grid;grid-template-columns:1fr 1fr;gap:1px;background:#d9e2df}.fact{padding:12px;background:#fff}.fact span{display:block;color:#6a7c80;font-size:10px;text-transform:uppercase}.fact b{display:block;margin-top:4px;font:700 13px ui-monospace,monospace}.freq{display:flex;justify-content:space-between;padding:10px 0;border-bottom:1px solid #dce4e2;font-size:13px}.freq b{font:700 13px ui-monospace,monospace}@media(max-width:680px){.layout{grid-template-columns:1fr;padding:8px}.panel{padding:13px}h1{font-size:26px}.runway{grid-template-columns:56px 1fr 58px}.badges{margin-top:10px}}
</style></head><body><header><h1>KPDX</h1><p>Portland International &middot; Oregon, United States</p><div class="badges"><span class="badge">D 31 FT</span><span class="badge">MAGVAR 15E</span><span class="badge">UTC-7</span><span class="badge">TOWERED</span></div></header><main class="layout"><section><div class="panel"><h2>Weather 12:38Z</h2><div class="metar">KPDX 161238Z 17008KT 10SM FEW025 SCT060 16/09 A3004</div></div><div class="panel" style="margin-top:14px"><h2>Runways</h2><div class="runway"><b>10L / 28R</b><div class="rwybar"></div><span>9,825 FT</span></div><div class="runway"><b>10R / 28L</b><div class="rwybar"></div><span>11,000 FT</span></div><div class="runway"><b>03 / 21</b><div class="rwybar"></div><span>6,000 FT</span></div></div></section><aside><div class="panel"><h2>Airport data</h2><div class="facts"><div class="fact"><span>Longest runway</span><b>11,000 FT</b></div><div class="fact"><span>Surface</span><b>CONCRETE</b></div><div class="fact"><span>Transition alt</span><b>18,000 FT</b></div><div class="fact"><span>Fuel</span><b>100LL / JET-A</b></div></div></div><div class="panel" style="margin-top:14px"><h2>Frequencies</h2><div class="freq"><span>ATIS</span><b>128.35</b></div><div class="freq"><span>Ground</span><b>121.90</b></div><div class="freq"><span>Tower</span><b>118.70</b></div><div class="freq"><span>Approach</span><b>124.35</b></div></div></aside></main></body></html>
'@

$statusPage = @'
<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Status</title><style>*{box-sizing:border-box}html,body{height:100%;margin:0;background:#14242b;color:#eaf1f1;font-family:system-ui,sans-serif}body{display:grid;place-items:center}.status{display:grid;grid-template-columns:auto 1fr;gap:10px 14px;padding:22px}.dot{width:12px;height:12px;margin-top:3px;border-radius:50%;background:#6bd39c}.label{color:#9fb4b8;font-size:12px}.value{font:700 15px ui-monospace,monospace}</style></head><body><div class="status"><i class="dot"></i><div><div class="label">Simulator</div><div class="value">CONNECTED</div></div><i class="dot"></i><div><div class="label">Last update</div><div class="value">12:43:18Z</div></div></div></body></html>
'@

$infoJson = '{"application":"Little Navmap","version":"mock-android-dev","zoom_ui":8.5,"latLonRect_ui":[49.5,-123.5,47.0,-121.0]}'
$activeSimInfoJson = '{"active":true,"simconnect_status":"Connected to Microsoft Flight Simulator","position":{"lat":47.887,"lon":-122.69},"indicated_speed":121.4,"true_airspeed":129.8,"ground_speed":126.2,"sea_level_pressure":1013.25,"vertical_speed":310.0,"indicated_altitude":7840.0,"ground_altitude":430.0,"altitude_above_ground":7410.0,"heading":164.2,"wind_direction":224.0,"wind_speed":18.0}'
$airportInfoJson = @'
{
  "ident": "KPDX",
  "icao": "KPDX",
  "iata": "PDX",
  "faa": "PDX",
  "local": "PDX",
  "name": "Portland International",
  "region": "US-OR",
  "city": "Portland",
  "state": "Oregon",
  "country": "United States",
  "closed": false,
  "elevation": 31,
  "magneticDeclination": 15.1,
  "position": {"lat": 45.588708, "lon": -122.596869},
  "rating": 5,
  "transitionAltitude": 18000,
  "facilities": ["3D", "Aprons", "Taxiways", "Tower Object", "Parking", "Avgas", "Jetfuel", "ILS", "VASI", "ALS"],
  "runways": ["Hard", "Lighted"],
  "parking": {
    "gates": 38,
    "jetWays": 24,
    "gaRamps": 31,
    "cargo": 12,
    "militaryCargo": 2,
    "militaryCombat": 1,
    "helipads": 3
  },
  "longestRunwayLength": 11000,
  "longestRunwayWidth": 150,
  "longestRunwayHeading": "101\u00b0M, 281\u00b0M",
  "longestRunwaySurface": "C",
  "metar": {
    "simulator": {
      "station": "KPDX 161238Z 17008KT 10SM FEW025 SCT060 16/09 A3004",
      "nearest": "KTTD 161253Z 16005KT 10SM CLR 15/09 A3005",
      "interpolated": "KPDX 161238Z 17008KT 10SM FEW025 SCT060 16/09 A3004"
    },
    "activesky": {
      "station": "KPDX 161238Z 17008KT 10SM FEW025 SCT060 16/09 A3004"
    },
    "noaa": {
      "station": "KPDX 161253Z 17008KT 10SM FEW025 SCT060 16/09 A3004",
      "nearest": "KTTD 161253Z 16005KT 10SM CLR 15/09 A3005"
    },
    "vatsim": {
      "station": "KPDX 161253Z 17008KT 10SM SCT025 16/09 A3004",
      "nearest": ""
    },
    "ivao": {
      "station": "",
      "nearest": "KTTD 161253Z 16005KT 10SM CLR 15/09 A3005"
    }
  },
  "sunrise": "12:36:20",
  "sunset": "03:57:40",
  "activeDateTime": "Thu Jul 16 12:38:00 2026 GMT",
  "activeDateTimeSource": "real date",
  "com": {
    "Tower:": 118775,
    "ATIS:": 120425000,
    "AWOS:": 128350,
    "ASOS:": 124350,
    "UNICOM:": 122950
  }
}
'@

$simInfoResponse = @{
    StatusCode = 200
    ReasonPhrase = 'OK'
    ContentType = 'application/json; charset=utf-8'
    Body = $activeSimInfoJson
}
if ($SimState -eq 'Inactive') {
    $simInfoResponse.Body = '{"active":false}'
} elseif ($SimState -eq 'HttpError') {
    $simInfoResponse.StatusCode = 503
    $simInfoResponse.ReasonPhrase = 'Service Unavailable'
    $simInfoResponse.Body = '{"error":"simulator fixture unavailable"}'
} elseif ($SimState -eq 'Malformed') {
    $simInfoResponse.Body = '{"active":true,"position":'
}

$apiRoutes = @{
    '/api/ui/info' = @{
        StatusCode = 200
        ReasonPhrase = 'OK'
        ContentType = 'application/json; charset=utf-8'
        Body = $infoJson
    }
    '/api/sim/info' = $simInfoResponse
}

$routes = @{
    '/'                = @{ ContentType = 'text/html; charset=utf-8'; Body = $rootPage }
    '/frontend.html'   = @{ ContentType = 'text/html; charset=utf-8'; Body = $frontendPage }
    '/map.html'        = @{ ContentType = 'text/html; charset=utf-8'; Body = $mapPage }
    '/flightplan.html' = @{ ContentType = 'text/html; charset=utf-8'; Body = $flightPlanPage }
    '/aircraft.html'   = @{ ContentType = 'text/html; charset=utf-8'; Body = $aircraftPage }
    '/progress.html'   = @{ ContentType = 'text/html; charset=utf-8'; Body = $progressPage }
    '/airport.html'    = @{ ContentType = 'text/html; charset=utf-8'; Body = $airportPage }
    '/status.html'     = @{ ContentType = 'text/html; charset=utf-8'; Body = $statusPage }
}

function Write-HttpResponse {
    param(
        [Parameter(Mandatory)] [System.IO.Stream] $Stream,
        [Parameter(Mandatory)] [int] $StatusCode,
        [Parameter(Mandatory)] [string] $ReasonPhrase,
        [Parameter(Mandatory)] [string] $ContentType,
        [Parameter(Mandatory)] [byte[]] $BodyBytes,
        [hashtable] $AdditionalHeaders = @{},
        [switch] $HeadersOnly
    )

    $headerLines = [System.Collections.Generic.List[string]]::new()
    $headerLines.Add("HTTP/1.1 $StatusCode $ReasonPhrase")
    $headerLines.Add("Content-Type: $ContentType")
    $headerLines.Add("Content-Length: $($BodyBytes.Length)")
    $headerLines.Add('Connection: close')
    $headerLines.Add('Cache-Control: no-store')
    $headerLines.Add('X-Content-Type-Options: nosniff')
    foreach ($name in $AdditionalHeaders.Keys) {
        $headerLines.Add("${name}: $($AdditionalHeaders[$name])")
    }
    $headerBytes = [System.Text.Encoding]::ASCII.GetBytes(($headerLines -join "`r`n") + "`r`n`r`n")
    $Stream.Write($headerBytes, 0, $headerBytes.Length)
    if (-not $HeadersOnly) {
        $Stream.Write($BodyBytes, 0, $BodyBytes.Length)
    }
    $Stream.Flush()
}

function Write-TextResponse {
    param(
        [Parameter(Mandatory)] [System.IO.Stream] $Stream,
        [Parameter(Mandatory)] [int] $StatusCode,
        [Parameter(Mandatory)] [string] $ReasonPhrase,
        [Parameter(Mandatory)] [string] $ContentType,
        [Parameter(Mandatory)] [string] $Body,
        [hashtable] $AdditionalHeaders = @{},
        [switch] $HeadersOnly
    )

    Write-HttpResponse `
        -Stream $Stream `
        -StatusCode $StatusCode `
        -ReasonPhrase $ReasonPhrase `
        -ContentType $ContentType `
        -BodyBytes ([System.Text.Encoding]::UTF8.GetBytes($Body)) `
        -AdditionalHeaders $AdditionalHeaders `
        -HeadersOnly:$HeadersOnly
}

function Get-RequestTargetInfo {
    param([Parameter(Mandatory)] [string] $RequestTarget)

    try {
        if ($RequestTarget -match '^https?://') {
            $requestUri = [System.Uri]::new($RequestTarget)
        } else {
            $requestUri = [System.Uri]::new("http://localhost$RequestTarget")
        }
        $queryParameters = @{}
        $rawQuery = $requestUri.Query.TrimStart('?')
        if (-not [string]::IsNullOrEmpty($rawQuery)) {
            foreach ($pair in $rawQuery.Split('&', [System.StringSplitOptions]::RemoveEmptyEntries)) {
                $parts = $pair.Split('=', 2)
                $name = [System.Uri]::UnescapeDataString($parts[0].Replace('+', ' '))
                $value = if ($parts.Length -eq 2) {
                    [System.Uri]::UnescapeDataString($parts[1].Replace('+', ' '))
                } else {
                    ''
                }
                $queryParameters[$name] = $value
            }
        }

        return [pscustomobject] @{
            Path = [System.Uri]::UnescapeDataString($requestUri.AbsolutePath)
            Query = $queryParameters
        }
    } catch {
        return $null
    }
}

function Get-AirportInfoResponse {
    param([AllowNull()] [string] $Ident)

    $contentType = 'application/json; charset=utf-8'
    if (-not [string]::Equals($Ident, 'KPDX', [System.StringComparison]::OrdinalIgnoreCase)) {
        return @{
            StatusCode = 404
            ReasonPhrase = 'Not Found'
            ContentType = $contentType
            Body = 'Airport not found'
        }
    }

    switch ($AirportState) {
        'Found' {
            return @{
                StatusCode = 200
                ReasonPhrase = 'OK'
                ContentType = $contentType
                Body = $airportInfoJson
            }
        }
        'NotFound' {
            return @{
                StatusCode = 404
                ReasonPhrase = 'Not Found'
                ContentType = $contentType
                Body = 'Airport not found'
            }
        }
        'HttpError' {
            return @{
                StatusCode = 503
                ReasonPhrase = 'Service Unavailable'
                ContentType = $contentType
                Body = '{"error":"airport fixture unavailable"}'
            }
        }
        'Malformed' {
            return @{
                StatusCode = 200
                ReasonPhrase = 'OK'
                ContentType = $contentType
                Body = '{"ident":"KPDX","position":'
            }
        }
    }
}

function Get-ContentType {
    param([Parameter(Mandatory)] [string] $Path)

    switch ([System.IO.Path]::GetExtension($Path).ToLowerInvariant()) {
        '.html' { return 'text/html; charset=utf-8' }
        '.htm'  { return 'text/html; charset=utf-8' }
        '.css'  { return 'text/css; charset=utf-8' }
        '.js'   { return 'text/javascript; charset=utf-8' }
        '.json' { return 'application/json; charset=utf-8' }
        '.svg'  { return 'image/svg+xml' }
        '.ico'  { return 'image/x-icon' }
        '.png'  { return 'image/png' }
        '.jpg'  { return 'image/jpeg' }
        '.jpeg' { return 'image/jpeg' }
        '.gif'  { return 'image/gif' }
        '.webp' { return 'image/webp' }
        '.avif' { return 'image/avif' }
        '.txt'  { return 'text/plain; charset=utf-8' }
        '.xml'  { return 'application/xml; charset=utf-8' }
        '.yaml' { return 'application/yaml; charset=utf-8' }
        '.yml'  { return 'application/yaml; charset=utf-8' }
        '.woff' { return 'font/woff' }
        '.woff2' { return 'font/woff2' }
        '.ttf'  { return 'font/ttf' }
        '.otf'  { return 'font/otf' }
        '.wasm' { return 'application/wasm' }
        '.mp4'  { return 'video/mp4' }
        '.webm' { return 'video/webm' }
        default { return 'application/octet-stream' }
    }
}

function Get-StaticResource {
    param(
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [string] $Root
    )

    # URL paths never need Windows separators or drive/alternate-stream syntax.
    if ($Path.IndexOf([char] 0) -ge 0 -or $Path.Contains('\') -or $Path.Contains(':')) {
        return [pscustomobject] @{ StatusCode = 403; ReasonPhrase = 'Forbidden' }
    }

    $segments = @($Path.Split('/', [System.StringSplitOptions]::RemoveEmptyEntries))
    if ($segments | Where-Object { $_ -eq '.' -or $_ -eq '..' }) {
        return [pscustomobject] @{ StatusCode = 403; ReasonPhrase = 'Forbidden' }
    }

    $relativePath = $Path.TrimStart([char[]] @('/')).Replace(
        [System.IO.Path]::AltDirectorySeparatorChar,
        [System.IO.Path]::DirectorySeparatorChar
    )
    $candidatePath = [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($Root, $relativePath))
    $rootPrefix = $Root.TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    ) + [System.IO.Path]::DirectorySeparatorChar
    $comparison = if ([System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT) {
        [System.StringComparison]::OrdinalIgnoreCase
    } else {
        [System.StringComparison]::Ordinal
    }

    if (-not $candidatePath.Equals($Root, $comparison) -and -not $candidatePath.StartsWith($rootPrefix, $comparison)) {
        return [pscustomobject] @{ StatusCode = 403; ReasonPhrase = 'Forbidden' }
    }

    $currentPath = $Root
    foreach ($segment in $segments) {
        $currentPath = Join-Path $currentPath $segment
        if (Test-Path -LiteralPath $currentPath) {
            $currentItem = Get-Item -LiteralPath $currentPath
            if (($currentItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                return [pscustomobject] @{ StatusCode = 403; ReasonPhrase = 'Forbidden' }
            }
        }
    }

    if (Test-Path -LiteralPath $candidatePath -PathType Container) {
        $candidatePath = Join-Path $candidatePath 'index.html'
    }
    if (-not (Test-Path -LiteralPath $candidatePath -PathType Leaf)) {
        return [pscustomobject] @{ StatusCode = 404; ReasonPhrase = 'Not Found' }
    }

    $candidateItem = Get-Item -LiteralPath $candidatePath
    if (($candidateItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        return [pscustomobject] @{ StatusCode = 403; ReasonPhrase = 'Forbidden' }
    }

    return [pscustomobject] @{
        StatusCode = 200
        ReasonPhrase = 'OK'
        ContentType = (Get-ContentType -Path $candidatePath)
        BodyBytes = [System.IO.File]::ReadAllBytes($candidatePath)
    }
}

$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $Port)

try {
    $listener.Start()
    if ($null -eq $resolvedWebRoot) {
        Write-Host "Little Navmap Android fixture mock listening on http://0.0.0.0:$Port/"
    } else {
        Write-Host "Little Navmap Android source-web mock listening on http://0.0.0.0:$Port/"
        Write-Host "Serving static files from $resolvedWebRoot"
    }
    Write-Host "Simulator API fixture: $SimState"
    Write-Host "Airport API fixture: $AirportState"
    Write-Host 'Press Ctrl+C to stop.'

    while ($true) {
        if (-not $listener.Pending()) {
            Start-Sleep -Milliseconds 50
            continue
        }

        $client = $listener.AcceptTcpClient()
        try {
            $client.NoDelay = $true
            # Chromium opens speculative idle sockets. Keep this short so the
            # single-threaded development fixture does not stall real requests.
            $client.ReceiveTimeout = 500
            $client.SendTimeout = 5000
            $stream = $client.GetStream()
            $reader = [System.IO.StreamReader]::new(
                $stream,
                [System.Text.Encoding]::ASCII,
                $false,
                4096,
                $true
            )

            try {
                $requestLine = $reader.ReadLine()
                if ([string]::IsNullOrWhiteSpace($requestLine)) {
                    continue
                }

                do {
                    $headerLine = $reader.ReadLine()
                } while ($null -ne $headerLine -and $headerLine.Length -gt 0)

                $requestParts = $requestLine.Split(' ', [System.StringSplitOptions]::RemoveEmptyEntries)
                if ($requestParts.Length -lt 3) {
                    Write-TextResponse -Stream $stream -StatusCode 400 -ReasonPhrase 'Bad Request' -ContentType 'text/plain; charset=utf-8' -Body 'Bad Request'
                    continue
                }

                $method = $requestParts[0].ToUpperInvariant()
                Write-Verbose ("Request: {0} {1}" -f $method, $requestParts[1])
                if ($method -ne 'GET' -and $method -ne 'HEAD') {
                    Write-TextResponse -Stream $stream -StatusCode 405 -ReasonPhrase 'Method Not Allowed' -ContentType 'text/plain; charset=utf-8' -Body 'Method Not Allowed' -AdditionalHeaders @{ Allow = 'GET, HEAD' }
                    continue
                }
                $headersOnly = $method -eq 'HEAD'

                $requestTargetInfo = Get-RequestTargetInfo -RequestTarget $requestParts[1]
                if ($null -eq $requestTargetInfo) {
                    Write-TextResponse -Stream $stream -StatusCode 400 -ReasonPhrase 'Bad Request' -ContentType 'text/plain; charset=utf-8' -Body 'Bad Request' -HeadersOnly:$headersOnly
                    continue
                }
                $path = $requestTargetInfo.Path

                if ($path.StartsWith('/api/', [System.StringComparison]::OrdinalIgnoreCase)) {
                    if ($path -eq '/api/airport/info') {
                        $ident = if ($requestTargetInfo.Query.ContainsKey('ident')) {
                            [string] $requestTargetInfo.Query['ident']
                        } else {
                            $null
                        }
                        $response = Get-AirportInfoResponse -Ident $ident
                        Write-TextResponse -Stream $stream -StatusCode $response.StatusCode -ReasonPhrase $response.ReasonPhrase -ContentType $response.ContentType -Body $response.Body -HeadersOnly:$headersOnly
                    } elseif ($apiRoutes.ContainsKey($path)) {
                        $response = $apiRoutes[$path]
                        Write-TextResponse -Stream $stream -StatusCode $response.StatusCode -ReasonPhrase $response.ReasonPhrase -ContentType $response.ContentType -Body $response.Body -HeadersOnly:$headersOnly
                    } else {
                        Write-TextResponse -Stream $stream -StatusCode 404 -ReasonPhrase 'Not Found' -ContentType 'text/plain; charset=utf-8' -Body 'Not Found' -HeadersOnly:$headersOnly
                    }
                } elseif ($null -ne $resolvedWebRoot -and ($path -eq '/plugins' -or $path -eq '/plugins/')) {
                    $pluginRoot = Join-Path $resolvedWebRoot 'plugins'
                    $pluginList = if (Test-Path -LiteralPath $pluginRoot -PathType Container) {
                        ((Get-ChildItem -LiteralPath $pluginRoot -Directory | Sort-Object Name).Name -join '/')
                    } else {
                        ''
                    }
                    Write-TextResponse -Stream $stream -StatusCode 200 -ReasonPhrase 'OK' -ContentType 'text/plain; charset=utf-8' -Body $pluginList -HeadersOnly:$headersOnly
                } elseif ($null -ne $resolvedWebRoot) {
                    $resource = Get-StaticResource -Path $path -Root $resolvedWebRoot
                    if ($resource.StatusCode -eq 200) {
                        Write-HttpResponse -Stream $stream -StatusCode 200 -ReasonPhrase 'OK' -ContentType $resource.ContentType -BodyBytes $resource.BodyBytes -HeadersOnly:$headersOnly
                    } else {
                        Write-TextResponse -Stream $stream -StatusCode $resource.StatusCode -ReasonPhrase $resource.ReasonPhrase -ContentType 'text/plain; charset=utf-8' -Body $resource.ReasonPhrase -HeadersOnly:$headersOnly
                    }
                } elseif ($routes.ContainsKey($path)) {
                    $response = $routes[$path]
                    Write-TextResponse -Stream $stream -StatusCode 200 -ReasonPhrase 'OK' -ContentType $response.ContentType -Body $response.Body -HeadersOnly:$headersOnly
                } else {
                    Write-TextResponse -Stream $stream -StatusCode 404 -ReasonPhrase 'Not Found' -ContentType 'text/plain; charset=utf-8' -Body 'Not Found' -HeadersOnly:$headersOnly
                }
            } finally {
                $reader.Dispose()
                $stream.Dispose()
            }
        } catch [System.IO.IOException] {
            Write-Warning "Request failed: $($_.Exception.Message)"
        } finally {
            $client.Dispose()
        }
    }
} finally {
    $listener.Stop()
    Write-Host 'Little Navmap Android development mock stopped.'
}
