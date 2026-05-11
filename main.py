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

class RotatingHologram(Image):
    angle = NumericProperty(0)
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.source = 'assets/icons/logo.png'
        with self.canvas.before:
            PushMatrix()
            self.rot = Rotate(angle=0, axis=(0, 1, 0))
            Color(0, 1, 1, 0.3)
            self.glow = Ellipse(size=(dp(200), dp(20)), pos=(self.x, self.y - dp(20)))
        with self.canvas.after:
            PopMatrix()
        Clock.schedule_interval(self.animate, 1/60.)

    def animate(self, dt):
        self.angle += 1
        self.rot.angle = self.angle
        self.rot.origin = self.center
        self.glow.pos = (self.center_x - dp(100), self.y - dp(10))

class CyberHUD(FloatLayout):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        
        # 1. Capa Grid & Scanner
        self.add_widget(CyberGrid())

        # 2. Logo Holográfico Central
        self.hologram = RotatingHologram(size_hint=(None, None), size=(dp(300), dp(300)), 
                                        pos_hint={'center_x': .5, 'center_y': .5}, opacity=0.2)
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
        btns.add_widget(Button(text="[ SCAN_QR ]", background_color=(0, 0.5, 0.5, 0.5), color=(0, 1, 1, 1)))
        btns.add_widget(Button(text="[ TEST_SYS ]", background_color=(0, 0.5, 0, 0.5), color=(0, 1, 0.5, 1), on_release=self.mock_payment))
        btns.add_widget(Button(text="[ ALERT_SOS ]", background_color=(0.5, 0, 0, 0.5), color=(1, 0, 0, 1)))
        main.add_widget(btns)

        self.add_widget(main)
        
        # Listener de Red
        threading.Thread(target=self.ntfy_listener, daemon=True).start()

    def ntfy_listener(self):
        topic = "wingpay_client_A2ZQV4"
        url = f"https://ntfy.sh/{topic}/json"
        while True:
            try:
                with requests.get(url, stream=True) as r:
                    for line in r.iter_lines():
                        if line:
                            data = json.loads(line)
                            if "message" in data:
                                msg = json.loads(data["message"])
                                if msg.get("sender") == "CELULAR": continue
                                self.add_card(msg)
            except: time.sleep(5)

    @mainthread
    def add_card(self, msg):
        card = NeonCard(
            bank=msg.get("bank", "YAPE"),
            name=msg.get("name", "Cliente Stark"),
            amt=msg.get("amt", "0.00"),
            time=datetime.now().strftime("%H:%M")
        )
        self.payment_list.add_widget(card, index=len(self.payment_list.children))
        self.terminal.text = f">_ DATA_RECEIVED: {msg.get('bank')} from {msg.get('name')}\n" + self.terminal.text

    def mock_payment(self, *args):
        self.add_card({"bank":"YAPE", "name":"Wilson Orellano", "amt":"888.00"})

class WingPayCyberApp(App):
    def build(self):
        return CyberHUD()

if __name__ == '__main__':
    WingPayCyberApp().run()
