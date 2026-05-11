import os
import threading
import requests
import json
from datetime import datetime
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.floatlayout import FloatLayout
from kivy.uix.scrollview import ScrollView
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.uix.image import AsyncImage, Image
from kivy.graphics import Color, RoundedRectangle, Ellipse, Line, Rectangle, Rotate, PushMatrix, PopMatrix
from kivy.clock import Clock, mainthread
from kivy.core.window import Window
from kivy.metrics import dp
from kivy.properties import StringProperty, ListProperty, NumericProperty

# --- PROTOCOLO STARK v59.1 PREMIUM MASTER ---
# Animación: Rotación Holográfica Vertical (Eje Y)

class RotatingLogo(Image):
    angle = NumericProperty(0)
    
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.source = 'assets/icons/logo.png'
        with self.canvas.before:
            PushMatrix()
            self.rot = Rotate(angle=0, axis=(0, 1, 0)) # Rotación vertical (moneda)
        with self.canvas.after:
            PopMatrix()
        self.bind(pos=self.update_canvas, size=self.update_canvas)
        Clock.schedule_interval(self.animate, 1/60.)

    def update_canvas(self, *args):
        self.rot.origin = self.center

    def animate(self, dt):
        self.angle += 1.5 # Velocidad elegante
        self.rot.angle = self.angle

class PremiumMessage(BoxLayout):
    def __init__(self, bank, name, amt, time, is_sos=False, **kwargs):
        super().__init__(**kwargs)
        self.orientation = 'horizontal'
        self.size_hint_y = None
        self.height = dp(80)
        self.padding = [dp(15), dp(5), dp(15), dp(5)]
        self.spacing = dp(12)

        # Determinar colores por banco
        if is_sos:
            bg_color = (1, 0.9, 0.9, 1)
            border_color = (1, 0.2, 0.2, 1)
            icon_color = (1, 0, 0, 1)
            logo_char = "🚨"
        elif bank == "YAPE":
            bg_color = (0.9, 1, 0.9, 1)
            border_color = (0.2, 0.8, 0.4, 1)
            icon_color = (0.4, 0.0, 0.5, 1) # Morado Yape
            logo_char = "Y"
        else: # BCP/PLIN/Otros
            bg_color = (0.9, 0.95, 1, 1)
            border_color = (0.1, 0.5, 0.9, 1)
            icon_color = (1, 0.5, 0, 1) # Naranja BCP
            logo_char = "B"

        # 1. Avatar Circular del Banco
        avatar_box = FloatLayout(size_hint=(None, None), size=(dp(50), dp(50)))
        with avatar_box.canvas.before:
            Color(*border_color, mode='rgba')
            self.avatar_circle = Ellipse(pos=(0, 0), size=(dp(50), dp(50)))
            Color(1, 1, 1, 1)
            self.avatar_inner = Ellipse(pos=(dp(2), dp(2)), size=(dp(46), dp(46)))
        
        avatar_box.add_widget(Label(text=logo_char, bold=True, font_size='20sp', 
                                   color=icon_color, pos_hint={'center_x': .5, 'center_y': .5}))
        self.add_widget(avatar_box)

        # 2. Burbuja de Mensaje (Estilo Chat Premium)
        content_box = BoxLayout(orientation='vertical', padding=[dp(15), dp(10)])
        with content_box.canvas.before:
            Color(*bg_color)
            self.rect = RoundedRectangle(pos=self.pos, size=self.size, radius=[(0, 20), (20, 20), (20, 20), (20, 20)])
            Color(0.8, 0.8, 0.8, 0.2) # Sombra suave
            self.line = Line(rounded_rectangle=(self.x, self.y, self.width, self.height, 20), width=1.1)

        self.bind(pos=self.update_graphics, size=self.update_rect)

        title_label = Label(text=f"{name}", bold=True, font_size='14sp', color=(0.1, 0.1, 0.2, 1), 
                           halign='left', size_hint_x=1)
        title_label.bind(size=title_label.setter('text_size'))
        content_box.add_widget(title_label)

        msg_label = Label(text=f"Recibió S/ {amt} en {bank}", font_size='13sp', color=(0.3, 0.3, 0.4, 1),
                         halign='left', size_hint_x=1)
        msg_label.bind(size=msg_label.setter('text_size'))
        content_box.add_widget(msg_label)

        self.add_widget(content_box)

        # 3. Hora
        time_label = Label(text=time, font_size='10sp', color=(0.6, 0.6, 0.7, 1), size_hint_x=None, width=dp(40))
        self.add_widget(time_label)

    def update_rect(self, instance, value):
        self.rect.size = instance.size
        self.line.rounded_rectangle = (self.x, self.y, self.width, self.height, 20)

    def update_graphics(self, instance, value):
        self.rect.pos = instance.pos
        self.avatar_circle.pos = (self.x + dp(15), self.y + dp(15))
        self.avatar_inner.pos = (self.x + dp(17), self.y + dp(17))

class PremiumMasterUI(FloatLayout):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        
        # 1. Capa de Fondo (Holograma Central Rotatorio)
        logo = RotatingLogo(size_hint=(None, None), size=(dp(240), dp(240)), 
                           pos_hint={'center_x': .5, 'center_y': .55}, opacity=0.1)
        self.add_widget(logo)

        # 2. Estructura Principal
        main = BoxLayout(orientation='vertical')

        # Top Bar (Premium Header)
        top_bar = BoxLayout(size_hint_y=None, height=dp(70), padding=[dp(20), 0], spacing=dp(15))
        with top_bar.canvas.before:
            Color(1, 1, 1, 1)
            Rectangle(pos=(0, Window.height - dp(70)), size=(Window.width, dp(70)))
            Color(0.9, 0.9, 0.9, 1)
            Line(points=[0, Window.height - dp(70), Window.width, Window.height - dp(70)], width=1)

        logo = Image(source='assets/icons/logo.png', size_hint=(None, None), size=(dp(40), dp(40)), 
                     pos_hint={'center_y': .5})
        top_bar.add_widget(logo)

        title_box = BoxLayout(orientation='vertical', size_hint_x=1, pos_hint={'center_y': .5})
        title_box.add_widget(Label(text="Importaciones Wing", bold=True, font_size='18sp', 
                                  color=(0.1, 0.1, 0.2, 1), halign='left', size_hint_x=1))
        self.lbl_status = Label(text="● Sistema Master Online", font_size='11sp', color=(0.2, 0.8, 0.4, 1), 
                               halign='left', size_hint_x=1)
        title_box.add_widget(self.lbl_status)
        top_bar.add_widget(title_box)
        
        main.add_widget(top_bar)

        # Chat Area
        self.scroll = ScrollView(do_scroll_x=False)
        self.chat_list = BoxLayout(orientation='vertical', size_hint_y=None, spacing=dp(10), padding=[0, dp(15)])
        self.chat_list.bind(minimum_height=self.chat_list.setter('height'))
        self.scroll.add_widget(self.chat_list)
        main.add_widget(self.scroll)

        # Bottom Bar (Premium Tools)
        bottom_bar = BoxLayout(size_hint_y=None, height=dp(80), padding=dp(15), spacing=dp(15))
        with bottom_bar.canvas.before:
            Color(1, 1, 1, 1)
            RoundedRectangle(pos=(dp(10), dp(10)), size=(Window.width - dp(20), dp(65)), radius=[25])
            Color(0, 0, 0, 0.05)
            Line(rounded_rectangle=(dp(10), dp(10), Window.width - dp(20), dp(65), 25), width=1.5)

        self.btn_scan = Button(text="📷", background_normal='', background_color=(0.95, 0.96, 1, 1), 
                              color=(0.1, 0.5, 0.9, 1), font_size='22sp', size_hint_x=None, width=dp(55))
        
        self.input_code = Label(text="wingpay_client_A2ZQV4", color=(0.4, 0.4, 0.5, 1), font_size='12sp')
        
        self.btn_test = Button(text="🧪", background_normal='', background_color=(0.95, 1, 0.96, 1), 
                              color=(0.2, 0.8, 0.4, 1), font_size='22sp', size_hint_x=None, width=dp(55))

        self.btn_sos = Button(text="🚨", background_normal='', background_color=(1, 0.95, 0.95, 1), 
                             color=(1, 0.2, 0.2, 1), font_size='22sp', size_hint_x=None, width=dp(55))

        bottom_bar.add_widget(self.btn_scan)
        bottom_bar.add_widget(self.input_code)
        bottom_bar.add_widget(self.btn_test)
        bottom_bar.add_widget(self.btn_sos)
        
        main.add_widget(bottom_bar)
        self.add_widget(main)

        # Lógica de Escucha
        threading.Thread(target=self.ntfy_listener, daemon=True).start()

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
                                msg = json.loads(data["message"])
                                if msg.get("sender") == "CELULAR": continue # Evitar eco
                                self.add_message_to_ui(msg)
            except: time.sleep(10)

    @mainthread
    def add_message_to_ui(self, msg):
        hora = datetime.now().strftime("%H:%M")
        is_sos = msg.get("type") == "SOS"
        item = PremiumMessage(
            bank=msg.get("bank", "YAPE"),
            name=msg.get("name", "Wilson Orellano"),
            amt=msg.get("amt", "0.00"),
            time=hora,
            is_sos=is_sos
        )
        self.chat_list.add_widget(item)
        Clock.schedule_once(lambda dt: setattr(self.scroll, 'scroll_y', 0), 0.1)

class WingPayPremiumApp(App):
    def build(self):
        return PremiumMasterUI()

if __name__ == '__main__':
    WingPayPremiumApp().run()
