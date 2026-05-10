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

# --- MOTOR PRINCIPAL: WING PAY SENTINEL v39.0 (STARK GLASS EDITION) ---
class WingPaySentinel(BoxLayout):
    status_ntfy = StringProperty("🔴") 
    status_pc = StringProperty("⚪")
    pulse_color = ListProperty([0.04, 0.6, 0.35, 0.5]) # Color de la "animación Lottie"

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.orientation = 'vertical'
        self.start_omega_sync()
        self.start_lottie_pulse()

    def start_lottie_pulse(self):
        # Simulación de animación Lottie: Pulso de luz circular
        Clock.schedule_interval(self._update_pulse, 0.05)

    def _update_pulse(self, dt):
        # Hace que el círculo de estado "respire"
        import math
        alpha = (math.sin(Clock.get_time() * 3) + 1) / 4 + 0.2
        self.pulse_color[3] = alpha

    def request_emui_permissions(self):
        try:
            from kivy.utils import platform
            if platform == 'android':
                from jnius import autoclass
                PythonActivity = autoclass('org.kivy.android.PythonActivity')
                Intent = autoclass('android.content.Intent')
                Settings = autoclass('android.provider.Settings')
                currentActivity = PythonActivity.mActivity
                intent_notif = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                currentActivity.startActivity(intent_notif)
                intent_bat = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                currentActivity.startActivity(intent_bat)
        except Exception as e:
            self.add_message(f"Error abriendo permisos: {e}", is_user=False)

    def start_omega_sync(self):
        threading.Thread(target=self.ntfy_listener_task, daemon=True).start()

    def ntfy_listener_task(self):
        topic = "wingpay_stark_8502345704"
        url = f"https://ntfy.sh/{topic}/json"
        while True:
            try:
                with requests.get(url, stream=True, timeout=None) as r:
                    self.status_ntfy = "🟢" 
                    for line in r.iter_lines():
                        if line:
                            self.status_pc = "🔵"
                            data = json.loads(line)
                            if "message" in data:
                                try:
                                    msg_data = json.loads(data["message"])
                                    self.handle_remote_payment(msg_data)
                                except: pass
                            Clock.schedule_once(lambda dt: setattr(self, 'status_pc', "⚪"), 2)
            except Exception as e:
                self.status_ntfy = "🔴"
                import time
                time.sleep(15)

    @mainthread
    def handle_remote_payment(self, data):
        bank = data.get("bank", "YAPE")
        name = data.get("name", "Cliente")
        amt = data.get("amt", "0.00")
        details = f"S/ {amt} de {name}"
        self.intercept_payment(bank, details, remote=True)

    def trigger_panic(self):
        if vibrator: vibrator.vibrate(0.5)
        self.add_message("🚨 ALARMA DE PÁNICO ACTIVADA 🚨", is_user=True)

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

    @mainthread
    def add_message(self, text, is_user=True, is_payment=False, bank="YAPE", source=""):
        # Colores Glassmorphism (Semi-transparentes)
        if is_payment:
            bg = [1, 1, 1, 0.2] # Vidrio esmerilado claro
            border = [0.1, 0.8, 0.4, 0.6] if bank == "YAPE" else [1, 0.6, 0.1, 0.6]
        else:
            bg = [1, 1, 1, 0.15] if is_user else [1, 1, 1, 0.25]
            border = [1, 1, 1, 0.3]
        
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
        Clock.schedule_once(lambda dt: self.add_message(f"Foto: {os.path.basename(path)}", is_user=True, source=path), 0)

    def intercept_payment(self, bank, details, remote=False):
        self.add_message(f"💰 {bank} CONFIRMADO\n{details}", is_user=False, is_payment=True, bank=bank)
        if not remote: threading.Thread(target=self.broadcast_to_mirror, args=(bank, "Cliente", "S/ 0.00")).start()

    def broadcast_to_mirror(self, bank, nombre, monto):
        self.status_pc = "🔵"
        try:
            requests.post(f"https://ntfy.sh/wingpay_stark_8502345704", data=json.dumps({"bank": bank, "name": nombre, "amt": monto}))
        except: self.status_pc = "🔴"
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
            width: min(Window.width * 0.75, self.minimum_width + 30) if not root.has_image else '260dp'
            height: self.minimum_height
            padding: [15, 12]
            canvas.before:
                # EFECTO GLASSMORPHISM
                Color:
                    rgba: root.bg_color
                RoundedRectangle:
                    pos: self.pos
                    size: self.size
                    radius: [18, 18, 2, 18] if root.is_user else [18, 18, 18, 2]
                Color:
                    rgba: root.border_color
                Line:
                    width: 1.2
                    rounded_rectangle: (self.x, self.y, self.width, self.height, 18, 18, 2, 18) if root.is_user else (self.x, self.y, self.width, self.height, 18, 18, 18, 2)

            AsyncImage:
                source: root.source
                size_hint_y: None
                height: '220dp' if root.has_image else 0
                opacity: 1 if root.has_image else 0
                allow_stretch: True

            Label:
                text: root.text
                color: 1, 1, 1, 1 # Texto blanco para destacar sobre el vidrio
                font_size: '16sp'
                size_hint: 1, None
                height: self.texture_size[1]
                text_size: self.width, None
                halign: 'left'
                bold: root.is_payment

            Label:
                text: root.time
                color: 1, 1, 1, 0.6
                font_size: '11sp'
                size_hint_y: None
                height: '18dp'
                halign: 'right'
                text_size: self.size

WingPaySentinel:
    BoxLayout:
        orientation: 'vertical'
        canvas.before:
            # FONDO GRADIENT (STARK STYLE: #0f2027 -> #203a43)
            Color:
                rgba: 1, 1, 1, 1
            Mesh:
                mode: 'triangle_fan'
                vertices: [self.x, self.y, 0, 0, 0.05, 0.12, 0.15, 1, self.right, self.y, 0, 0, 0.12, 0.22, 0.26, 1, self.right, self.top, 0, 0, 0.15, 0.25, 0.28, 1, self.x, self.top, 0, 0, 0.05, 0.12, 0.15, 1]
                indices: [0, 1, 2, 3]

        # CABECERA GLASS
        BoxLayout:
            size_hint_y: None
            height: '90dp'
            padding: '15dp'
            spacing: '10dp'
            canvas.before:
                Color:
                    rgba: 1, 1, 1, 0.1
                Rectangle:
                    pos: self.pos
                    size: self.size
            
            BoxLayout:
                orientation: 'vertical'
                Label:
                    text: "WING PAY STARK OS"
                    bold: True
                    font_size: '20sp'
                    halign: 'left'
                    text_size: self.size
                BoxLayout:
                    spacing: '15dp'
                    Label:
                        text: f"SISTEMA: {root.status_ntfy}  SYNC: {root.status_pc}"
                        font_size: '13sp'
                        color: 0.8, 0.9, 1, 1
                        halign: 'left'
                        text_size: self.size

            # ESPACIO PARA ANIMACIÓN LOTTIE (PULSO DE ESTADO)
            Widget:
                size_hint: None, None
                size: '40dp', '40dp'
                canvas:
                    Color:
                        rgba: root.pulse_color
                    Ellipse:
                        pos: self.x, self.y
                        size: self.size
                    Color:
                        rgba: 1, 1, 1, 0.8
                    Line:
                        width: 1.5
                        circle: (self.center_x, self.center_y, 15)

            Button:
                text: "🛠"
                size_hint_x: None
                width: '50dp'
                background_color: 1, 1, 1, 0.1
                on_release: root.request_emui_permissions()
            Button:
                text: "🚨"
                size_hint_x: None
                width: '50dp'
                background_color: 0.8, 0.1, 0.1, 0.6
                on_release: root.trigger_panic()

        # CHAT RECYCLEVIEW
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

        # BARRA DE ENTRADA GLASS
        BoxLayout:
            size_hint_y: None
            height: '80dp'
            padding: '10dp'
            spacing: '12dp'
            canvas.before:
                Color:
                    rgba: 1, 1, 1, 0.08
                Rectangle:
                    pos: self.pos
                    size: self.size

            Button:
                text: "📎"
                size_hint_x: None
                width: '50dp'
                background_color: 1, 1, 1, 0.1
                on_release: root.select_media()

            TextInput:
                id: ti
                hint_text: "Mensaje cifrado..."
                multiline: False
                background_color: 1, 1, 1, 0.1
                foreground_color: 1, 1, 1, 1
                cursor_color: 0, 0.8, 1, 1
                on_text_validate: root.send_action(ti)
            
            Button:
                text: "➤"
                size_hint_x: None
                width: '60dp'
                background_color: 0, 0.5, 0.8, 0.6
                on_release: root.send_action(ti)
''')



if __name__ == '__main__':
    WingPayApp().run()
