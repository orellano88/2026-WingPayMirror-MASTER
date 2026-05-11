import os
import threading
import requests
import json
import random
from datetime import datetime
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.floatlayout import FloatLayout
from kivy.uix.scrollview import ScrollView
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.uix.image import Image
from kivy.uix.widget import Widget
from kivy.graphics import Color, RoundedRectangle, Ellipse, Line, Rectangle, Rotate, PushMatrix, PopMatrix, InstructionGroup
from kivy.clock import Clock, mainthread
from kivy.core.window import Window
from kivy.metrics import dp
from kivy.properties import StringProperty, ListProperty, NumericProperty, ObjectProperty

# --- PROTOCOLO STARK v60.0: CYBER-HOLOGRAPHIC HUD (GOD MODE) ---

Window.clearcolor = (0.01, 0.02, 0.05, 1) # Negro profundo Cyberpunk

class CyberGrid(Widget):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        with self.canvas.before:
            Color(0, 0.5, 1, 0.1) # Azul tenue
            for i in range(0, 2000, 40):
                Line(points=[i, 0, i, 2000], width=1)
                Line(points=[0, i, 2000, i], width=1)
            # Escáner móvil
            Color(0, 1, 1, 0.2)
            self.scanner = Rectangle(pos=(0, 0), size=(2000, dp(2)))
        Clock.schedule_interval(self.update_scanner, 1/60.)

    def update_scanner(self, dt):
        y = (self.scanner.pos[1] + 5) % Window.height
        self.scanner.pos = (0, y)

class NeonCard(BoxLayout):
    def __init__(self, bank, name, amt, time, is_sos=False, **kwargs):
        super().__init__(**kwargs)
        self.orientation = 'vertical'
        self.size_hint_y = None
        self.height = dp(100)
        self.padding = dp(15)
        
        # Colores Neon
        self.neon_color = (1, 0, 0.5, 1) if is_sos else (0, 1, 1, 1) # Magenta vs Cian
        
        with self.canvas.before:
            # Fondo de cristal oscuro
            Color(0, 0, 0, 0.6)
            self.bg = RoundedRectangle(pos=self.pos, size=self.size, radius=[15])
            # Borde Neon
            Color(*self.neon_color)
            self.border = Line(rounded_rectangle=(self.x, self.y, self.width, self.height, 15), width=dp(1.5))
        
        self.bind(pos=self.update_rect, size=self.update_rect)
        
        header = f"[b]{bank}[/b] | {time}"
        self.add_widget(Label(text=header, markup=True, color=self.neon_color, font_size='12sp', halign='left', size_hint_x=1))
        self.add_widget(Label(text=name.upper(), bold=True, font_size='16sp', color=(1, 1, 1, 1), halign='left', size_hint_x=1))
        self.add_widget(Label(text=f"S/ {amt}", font_size='22sp', color=self.neon_color, bold=True, halign='right', size_hint_x=1))

    def update_rect(self, *args):
        self.bg.pos = self.pos
        self.bg.size = self.size
        self.border.rounded_rectangle = (self.x, self.y, self.width, self.height, 15)

class StaticHologram(Image):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.source = 'assets/icons/logo.png'
        with self.canvas.before:
            Color(0, 1, 1, 0.2)
            self.glow = Ellipse(size=(dp(240), dp(24)), pos=(self.x, self.y))
        self.bind(pos=self.update_graphics, size=self.update_graphics)

    def update_graphics(self, *args):
        self.glow.pos = (self.center_x - dp(120), self.center_y - dp(110))

class CyberHUD(FloatLayout):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        
        # 1. Capa Grid & Scanner
        self.add_widget(CyberGrid())

        # 2. Logo Holográfico Central (Estatico)
        self.hologram = StaticHologram(size_hint=(None, None), size=(dp(280), dp(280)), 
                                        pos_hint={'center_x': .5, 'center_y': .55}, opacity=0.15)
        self.add_widget(self.hologram)

        # 3. Layout de Contenido
        main = BoxLayout(orientation='vertical', padding=dp(20), spacing=dp(15))
        
        # Header Técnico
        header = BoxLayout(size_hint_y=None, height=dp(80))
        title = Label(text="STARK_OS // v60.0_GOD_MODE\n[IMPORTACIONES_WING_CORE]", 
                      bold=True, font_size='18sp', color=(0, 1, 1, 1), halign='left')
        header.add_widget(title)
        
        self.lbl_status = Label(text="STATUS: ONLINE\nSYNC: MASTER", color=(0, 1, 0.5, 1), font_size='10sp', size_hint_x=None, width=dp(100))
        header.add_widget(self.lbl_status)
        main.add_widget(header)

        # Binary Stream Terminal
        self.terminal = Label(text=">_ INITIALIZING_HOLOGRAPHIC_INTERFACE...\n>_ SCANNING_NETWORK_NODES...\n>_ LINK_ESTABLISHED: wingpay_client_A2ZQV4",
                             font_size='11sp', color=(0, 1, 0.5, 0.8), halign='left', valign='top', size_hint_y=None, height=dp(120), font_name='Roboto')
        self.terminal.bind(size=lambda *x: setattr(self.terminal, 'text_size', self.terminal.size))
        main.add_widget(self.terminal)

        # Scroll de Pagos
        self.scroll = ScrollView(do_scroll_x=False)
        self.payment_list = BoxLayout(orientation='vertical', size_hint_y=None, spacing=dp(12))
        self.payment_list.bind(minimum_height=self.payment_list.setter('height'))
        self.scroll.add_widget(self.payment_list)
        main.add_widget(self.scroll)

        # Botonera Cyber
        btns = BoxLayout(size_hint_y=None, height=dp(60), spacing=dp(10))
        btns.add_widget(Button(text="[ SCAN_QR ]", background_color=(0, 0.5, 0.5, 0.5), color=(0, 1, 1, 1), on_release=self.scan_qr))
        btns.add_widget(Button(text="[ TEST_SYS ]", background_color=(0, 0.5, 0, 0.5), color=(0, 1, 0.5, 1), on_release=self.run_test))
        btns.add_widget(Button(text="[ ALERT_SOS ]", background_color=(0.5, 0, 0, 0.5), color=(1, 0, 0, 1), on_release=self.trigger_sos))
        main.add_widget(btns)

        self.add_widget(main)
        
        # Listener de Red
        threading.Thread(target=self.ntfy_listener, daemon=True).start()

    def broadcast_to_mirror(self, bank, name, amt, is_sos=False):
        topic = "wingpay_client_A2ZQV4"
        url = f"https://ntfy.sh/{topic}"
        payload = {
            "bank": bank,
            "name": name,
            "amt": amt,
            "sender": "CELULAR",
            "type": "SOS" if is_sos else "PAYMENT",
            "time": datetime.now().strftime("%H:%M:%S")
        }
        try:
            requests.post(url, data=json.dumps(payload), timeout=5)
            self.terminal.text = f">_ SYNC_SENT: {bank} TO_PC\n" + self.terminal.text
        except:
            self.terminal.text = ">_ ERROR: PC_SYNC_FAILED\n" + self.terminal.text

    def run_test(self, *args):
        self.terminal.text = ">_ CMD: TRIGGER_MASTER_TEST\n" + self.terminal.text
        threading.Thread(target=self.broadcast_to_mirror, args=("STARK_OS", "PRUEBA_GOD_LEVEL", "777.00")).start()

    def trigger_sos(self, *args):
        self.terminal.text = ">_ ALERTA: SOS_BROADCAST_ACTIVE\n" + self.terminal.text
        threading.Thread(target=self.broadcast_to_mirror, args=("SOS", "EMERGENCIA", "0", True)).start()

    def scan_qr(self, *args):
        self.terminal.text = ">_ CMD: START_ZBAR_SCANNER\n" + self.terminal.text

    def ntfy_listener(self):
        topic = "wingpay_client_A2ZQV4"
        url = f"https://ntfy.sh/{topic}/json"
        while True:
            try:
                with requests.get(url, stream=True, timeout=None) as r:
                    for line in r.iter_lines():
                        if line:
                            data = json.loads(line)
                            if "message" in data:
                                try:
                                    msg = json.loads(data["message"])
                                    if msg.get("sender") == "CELULAR": continue # Evitar eco
                                    self.add_card(msg)
                                except: pass
            except: 
                self.terminal.text = ">_ RED: RECONECTANDO...\n" + self.terminal.text
                time.sleep(5)

    @mainthread
    def add_card(self, msg):
        is_sos = msg.get("type") == "SOS"
        card = NeonCard(
            bank=msg.get("bank", "YAPE"),
            name=msg.get("name", "Cliente Stark"),
            amt=msg.get("amt", "0.00"),
            time=datetime.now().strftime("%H:%M"),
            is_sos=is_sos
        )
        self.payment_list.add_widget(card, index=len(self.payment_list.children))
        icon = "🚨" if is_sos else "💰"
        self.terminal.text = f">_ DATA_RECEIVED: {icon} {msg.get('bank')} FROM_PC\n" + self.terminal.text

class WingPayCyberApp(App):
    def build(self):
        Window.title = "STARK OS v62.1 MASTER GOD"
        return CyberHUD()

if __name__ == '__main__':
    WingPayCyberApp().run()
