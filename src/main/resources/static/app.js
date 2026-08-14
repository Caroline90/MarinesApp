const map = L.map('map', { zoomControl: false }).setView([53.912, 14.254], 13);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 18, attribution: '&copy; OpenStreetMap' }).addTo(map);
const markers = new Map();
const ids = ['connectionState','subscriptionState','visibleCount','redisCount','aisMessageCounter','positionReportCounter','lastAisMessageType','lastAisError'];
const el = Object.fromEntries(ids.map(id => [id, document.getElementById(id)]));
const selected = document.getElementById('selectedVessel');
const search = document.getElementById('search');

function icon(){return L.divIcon({className:'vessel-marker',iconSize:[16,16]});}
function matches(v){const q=search.value.trim().toLowerCase();return !q || v.mmsi.toLowerCase().includes(q) || v.name.toLowerCase().includes(q);}
function show(v){selected.innerHTML=`<b>${v.name}</b><br>MMSI ${v.mmsi}<br><small>${v.messageType} · ${v.speedOverGround.toFixed(1)} kn · COG ${v.courseOverGround.toFixed(0)}°</small>`;}
async function refresh(){
  const [diag, vessels] = await Promise.all([fetch('/api/v1/system/ais').then(r=>r.json()), fetch('/api/v1/system/ais/vessels').then(r=>r.json())]);
  el.connectionState.textContent = diag.connectionState;
  el.subscriptionState.textContent = diag.subscriptionState;
  el.redisCount.textContent = diag.redisVesselCount;
  el.aisMessageCounter.textContent = diag.aisMessageCounter;
  el.positionReportCounter.textContent = diag.positionReportCounter;
  el.lastAisMessageType.textContent = diag.lastAisMessageType;
  el.lastAisError.textContent = diag.lastAisError;
  let visible = 0;
  vessels.forEach(v => {
    if (!matches(v)) return;
    visible++;
    const point = [v.latitude, v.longitude];
    const marker = markers.get(v.mmsi) || L.marker(point, {icon: icon()}).addTo(map).on('click', () => show(v));
    marker.setLatLng(point).bindTooltip(`${v.name} (${v.mmsi})`);
    markers.set(v.mmsi, marker);
  });
  markers.forEach((marker, mmsi) => { if (!vessels.some(v => v.mmsi === mmsi && matches(v))) { map.removeLayer(marker); markers.delete(mmsi); } });
  el.visibleCount.textContent = visible;
}
search.addEventListener('input', refresh);
refresh();
setInterval(refresh, 2500);
