import os
import threading
import requests
import json
from datetime import datetime
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.recycleview import RecycleView
from kivy.uix.image import AsyncImage
from kivy.graphics import Color, RoundedRectangle, Line
from kivy.clock import Clock, mainthread
from kivy.core.window import Window
from kivy.properties import StringProperty, BooleanProperty, ListProperty, NumericProperty
from kivy.lang import Builder

# --- CONFIGURACIÓN DE PANTALLA STARK ---
Window.softinput_mode = "below_target"
Window.clearcolor = (0.94, 0.95, 0.96, 1)

try:
    from plyer import tts, vibrator, notification, camera, filechooser
except ImportError:
    tts = vibrator = notification = camera = filechooser = None

# --- COMPONENTE: BURBUJA DE MENSAJE WHATSAPP ---
class MessageBubble(BoxLayout):
    text = StringProperty("")
    source = StringProperty("")
    is_user = BooleanProperty(True)
    is_payment = BooleanProperty(False)
    bank = StringProperty("YAPE")
    time = StringProperty("")
    bg_color = ListProperty([1, 1, 1, 1])
    border_color = ListProperty([1, 1, 1, 1])
    halign = StringProperty("right")
    has_image = BooleanProperty(False)

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.orientation = 'vertical'
        self.size_hint_y = None
        self.spacing = 5
        self.bind(minimum_height=self.setter('height'))

# --- MOTOR PRINCIPAL: WING PAY SENTINEL v40.3 (STARK TOTAL TEST EDITION) ---
class WingPaySentinel(BoxLayout):
    status_ntfy = StringProperty("🔴") 
    status_pc = StringProperty("⚪")
    pulse_color = ListProperty([0, 0.8, 1, 0.5]) # Azul Stark
    terminal_logs = ListProperty([]) 
    is_speaking = BooleanProperty(False) 

    def stark_total_test(self):
        # El mensaje que pediste, dinámico y motivador
        mensaje = "¡Prueba Stark exitosa! Hoy tendrás una venta maestra superior a los 10 mil soles. ¡A por ello, jefe!"
        self.log_to_terminal("INICIANDO_TEST_TOTAL_STARK")
        
        # 1. Mostrar en UI
        self.handle_remote_payment({"bank": "STARK", "name": "VENTA MAESTRA", "amt": "10,000.00"})
        
        # 2. Hablar (Puente Nativo)
        self.speak_stark(mensaje)
        
        # 3. Sincronizar con PC
        self.broadcast_to_mirror("STARK", "VENTA_MAESTRA", "10000.00")
        self.log_to_terminal("TEST_TOTAL: UI, AUDIO Y SYNC ACTIVADOS")

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.orientation = 'vertical'
        self.start_omega_sync()
        self.start_stark_animations()

    def start_stark_animations(self):
        Clock.schedule_interval(self._update_stark_fx, 0.05)

    def _update_stark_fx(self, dt):
        import math
        t = Clock.get_time()
        # Pulso dinámico: si está hablando, brilla con más fuerza y velocidad
        base_speed = 8 if self.is_speaking else 4
        base_alpha = 0.5 if self.is_speaking else 0.2
        self.pulse_color[3] = (math.sin(t * base_speed) + 1) / 4 + base_alpha

    def log_to_terminal(self, msg):
        time_str = datetime.now().strftime("%H:%M:%S")
        self.terminal_logs.append(f"[{time_str}] STARK_LOG: {msg}")
        if len(self.terminal_logs) > 50: self.terminal_logs.pop(0)

    def request_emui_permissions(self):
        try:
            from kivy.utils import platform
            if platform == 'android':
                from jnius import autoclass
                PythonActivity = autoclass('org.kivy.android.PythonActivity')
                Intent = autoclass('android.content.Intent')
                Settings = autoclass('android.provider.Settings')
                currentActivity = PythonActivity.mActivity
                currentActivity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                Clock.schedule_once(lambda dt: currentActivity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)), 1)
        except: pass

    def start_omega_sync(self):
        threading.Thread(target=self.ntfy_listener_task, daemon=True).start()

    def ntfy_listener_task(self):
        topic = "wingpay_stark_8502345704"
        url = f"https://ntfy.sh/{topic}/json"
        self.log_to_terminal(f"Conectando a Red Stark... {topic}")
        while True:
            try:
                with requests.get(url, stream=True, timeout=None) as r:
                    self.status_ntfy = "🟢" 
                    self.log_to_terminal("ENLACE ESTABLECIDO CON SATÉLITE NTFY")
                    for line in r.iter_lines():
                        if line:
                            self.status_pc = "🔵"
                            data = json.loads(line)
                            if "message" in data:
                                self.log_to_terminal(f"DATOS_ENTRANTES: {data['message'][:30]}...")
                                try:
                                    msg_data = json.loads(data["message"])
                                    if "stark_log" in msg_data:
                                        self.log_to_terminal(f"STATUS_CORE: {msg_data['stark_log']}")
                                    self.handle_remote_payment(msg_data)
                                except: pass
                            Clock.schedule_once(lambda dt: setattr(self, 'status_pc', "⚪"), 2)
            except Exception as e:
                self.status_ntfy = "🔴"
                self.log_to_terminal(f"ERROR_ENLACE: {e}")
                import time
                time.sleep(15)

    @mainthread
    def handle_remote_payment(self, data):
        bank = data.get("bank", "YAPE")
        name = data.get("name", "Cliente")
        amt = data.get("amt", "0.00")
        details = f"S/ {amt} de {name}"
        # En v40.1 activamos voz también para pagos remotos
        self.intercept_payment(bank, details, remote=True)

    def trigger_panic(self):
        if vibrator: vibrator.vibrate(0.5)
        self.add_message("🚨 PROTOCOLO DE PÁNICO ACTIVADO 🚨", is_user=True)
        self.log_to_terminal("ALERTA: Pánico activado.")
        self.speak_stark("Alerta de pánico detectada. Protocolo de seguridad activo.")

    def select_media(self):
        if filechooser: filechooser.open_file(on_selection=self._on_selection)
        else: self.add_message("📎 Abriendo Galería...", is_user=True)

    def _on_selection(self, selection):
        if selection: self.display_media(selection[0])

    def send_action(self, text_input):
        msg = text_input.text.strip()
        if msg:
            text_input.text = ""
            self.add_message(msg, is_user=True)
            self.log_to_terminal(f"COMANDO_MANUAL: {msg}")
            if msg.lower() == "test yape":
                self.handle_remote_payment({"bank": "YAPE", "name": "TEST LOCAL", "amt": "1.00"})

    @mainthread
    def add_message(self, text, is_user=True, is_payment=False, bank="YAPE", source=""):
        if is_payment:
            bg = [0.1, 0.4, 0.5, 0.4] 
            border = [0, 0.8, 1, 0.8] 
        else:
            bg = [1, 1, 1, 0.1]
            border = [1, 1, 1, 0.25]
        
        align = "center" if is_payment else ("right" if is_user else "left")
        new_entry = {
            "text": text,
            "source": source,
            "has_image": True if source else False,
            "is_user": is_user,
            "is_payment": is_payment,
            "bank": bank,
            "time": datetime.now().strftime("%H:%M"),
            "bg_color": bg,
            "border_color": border,
            "halign": align
        }
        self.ids.rv.data.append(new_entry)
        self.ids.rv.scroll_y = 0

    def display_media(self, path):
        Clock.schedule_once(lambda dt: self.add_message(f"Archivo_Stark: {os.path.basename(path)}", is_user=True, source=path), 0)

    def intercept_payment(self, bank, details, remote=False):
        self.add_message(f"💎 PAGO {bank} CONFIRMADO\n{details}", is_user=False, is_payment=True, bank=bank)
        
        # Lógica de Voz Optimizada v40.1
        monto = "un pago"
        if "S/" in details:
            parts = details.split("S/")
            if len(parts) > 1: monto = f"S/ {parts[1].split()[0]}"
        nombre = details.replace(f"por {monto}", "").replace(monto, "").replace("de", "").strip()
        
        speech = f"Atención. Pago recibido en {bank}. {nombre} envió {monto}."
        self.speak_stark(speech)

        if not remote: 
            self.log_to_terminal(f"DETECTADO_LOCAL: {bank}")
            threading.Thread(target=self.broadcast_to_mirror, args=(bank, nombre, monto)).start()

    def speak_stark(self, text):
        # PROTOCOLO STARK v40.2: Puente Universal de Audio
        # En lugar de usar Plyer (flaky), enviamos un Intent al servicio Kotlin
        self.is_speaking = True
        self.log_to_terminal("PUENTE_AUDIO: ENVIANDO_INTENT_NATIVO")
        
        try:
            from kivy.utils import platform
            if platform == 'android':
                from jnius import autoclass
                PythonActivity = autoclass('org.kivy.android.PythonActivity')
                Intent = autoclass('android.content.Intent')
                ComponentName = autoclass('android.content.ComponentName')
                context = PythonActivity.mActivity
                
                # Crear el Intent dirigido específicamente a nuestro servicio
                intent = Intent()
                intent.setComponent(ComponentName("com.inversioneswing.paymirror", "com.inversioneswing.paymirror.StarkCaptureService"))
                intent.putExtra("TEST_VOICE", text)
                
                # Iniciar el servicio con el extra de voz (activará awakeAndSpeak en Kotlin)
                context.startService(intent)
                self.log_to_terminal("AUDIO_SENTINEL: INTENT_ENTREGADO_OK")
            else:
                self.log_to_terminal(f"SIM_AUDIO: {text}")
        except Exception as e:
            self.log_to_terminal(f"ERROR_PUENTE_AUDIO: {e}")
        
        # Resetear el brillo del círculo después de 5s
        Clock.schedule_once(lambda dt: setattr(self, 'is_speaking', False), 5)

    def broadcast_to_mirror(self, bank, nombre, monto):
        self.status_pc = "🔵"
        try:
            requests.post(f"https://ntfy.sh/wingpay_stark_8502345704", data=json.dumps({"bank": bank, "name": nombre, "amt": monto, "stark_log": "SYNC_OK"}))
        except: 
            self.status_pc = "🔴"
            self.log_to_terminal("FALLO_SINC_NUBE")
        finally: Clock.schedule_once(lambda dt: setattr(self, 'status_pc', "⚪"), 3)

class WingPayApp(App):
    def build(self):
        return Builder.load_string('''
<MessageBubble>:
    padding: [10, 5]
    AnchorLayout:
        anchor_x: root.halign
        BoxLayout:
            orientation: 'vertical'
            size_hint: None, None
            width: min(Window.width * 0.8, self.minimum_width + 40) if not root.has_image else '280dp'
            height: self.minimum_height
            padding: [16, 12]
            canvas.before:
                Color:
                    rgba: root.bg_color
                RoundedRectangle:
                    pos: self.pos
                    size: self.size
                    radius: [20, 20, 4, 20] if root.is_user else [20, 20, 20, 4]
                Color:
                    rgba: root.border_color
                Line:
                    width: 1.5
                    rounded_rectangle: (self.x, self.y, self.width, self.height, 20, 20, 4, 20) if root.is_user else (self.x, self.y, self.width, self.height, 20, 20, 20, 4)

            AsyncImage:
                source: root.source
                size_hint_y: None
                height: '240dp' if root.has_image else 0
                opacity: 1 if root.has_image else 0
                allow_stretch: True

            Label:
                text: root.text
                color: 1, 1, 1, 1
                font_size: '17sp'
                size_hint: 1, None
                height: self.texture_size[1]
                text_size: self.width, None
                halign: 'left'
                bold: root.is_payment

            Label:
                text: root.time
                color: 1, 1, 1, 0.5
                font_size: '11sp'
                size_hint_y: None
                height: '18dp'
                halign: 'right'
                text_size: self.size

WingPaySentinel:
    BoxLayout:
        orientation: 'vertical'
        canvas.before:
            Color:
                rgba: 1, 1, 1, 1
            Mesh:
                mode: 'triangle_fan'
                vertices: [self.x, self.y, 0, 0, 0.02, 0.1, 0.15, 1, self.right, self.y, 0, 0, 0.1, 0.2, 0.25, 1, self.right, self.top, 0, 0, 0.15, 0.25, 0.3, 1, self.x, self.top, 0, 0, 0.02, 0.1, 0.15, 1]
                indices: [0, 1, 2, 3]

        BoxLayout:
            size_hint_y: None
            height: '100dp'
            padding: '15dp'
            spacing: '15dp'
            canvas.before:
                Color:
                    rgba: 1, 1, 1, 0.05
                Rectangle:
                    pos: self.pos
                    size: self.size
            
            BoxLayout:
                orientation: 'vertical'
                Label:
                    text: "STARK OS v40.3 PERFECTED"
                    bold: True
                    font_size: '22sp'
                    color: 0, 0.8, 1, 1
                    halign: 'left'
                    text_size: self.size
                Label:
                    text: f"ENLACE: {root.status_ntfy}  ESPEJO: {root.status_pc}"
                    font_size: '12sp'
                    color: 0.7, 0.7, 0.7, 1
                    halign: 'left'
                    text_size: self.size

            Widget:
                size_hint: None, None
                size: '45dp', '45dp'
                canvas:
                    Color:
                        rgba: root.pulse_color
                    Ellipse:
                        pos: self.x, self.y
                        size: self.size
                    Color:
                        rgba: 1, 1, 1, 0.9
                    Line:
                        width: 2
                        circle: (self.center_x, self.center_y, 18)

            Button:
                text: "⚙"
                size_hint_x: None
                width: '50dp'
                background_color: 1, 1, 1, 0.1
                font_size: '24sp'
                on_release: root.request_emui_permissions()
            Button:
                text: "🚨"
                size_hint_x: None
                width: '50dp'
                background_color: 1, 0, 0, 0.4
                on_release: root.trigger_panic()

        BoxLayout:
            size_hint_y: 0.25
            orientation: 'vertical'
            canvas.before:
                Color:
                    rgba: 0, 0, 0, 0.4
                Rectangle:
                    pos: self.pos
                    size: self.size
            ScrollView:
                Label:
                    text: "\\n".join(root.terminal_logs)
                    font_size: '10sp'
                    color: 0, 1, 0.4, 0.8
                    size_hint_y: None
                    height: self.texture_size[1]
                    text_size: self.width, None
                    padding: [10, 5]

        RecycleView:
            id: rv
            viewclass: 'MessageBubble'
            RecycleBoxLayout:
                default_size: None, None
                default_size_hint: 1, None
                size_hint_y: None
                height: self.minimum_height
                orientation: 'vertical'
                spacing: '15dp'
                padding: '15dp'

        BoxLayout:
            size_hint_y: None
            height: '85dp'
            padding: '12dp'
            spacing: '15dp'
            canvas.before:
                Color:
                    rgba: 1, 1, 1, 0.05
                Rectangle:
                    pos: self.pos
                    size: self.size

            TextInput:
                id: ti
                hint_text: "Inyectar comando..."
                multiline: False
                background_color: 1, 1, 1, 0.05
                foreground_color: 1, 1, 1, 1
                font_size: '16sp'
                on_text_validate: root.send_action(ti)
            
            Button:
                text: "➤"
                size_hint_x: None
                width: '65dp'
                background_color: 0, 0.6, 1, 0.5
                on_release: root.send_action(ti)
''')





if __name__ == '__main__':
    WingPayApp().run()
