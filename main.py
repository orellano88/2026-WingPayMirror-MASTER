import os
import threading
import requests
import json
import random
import time
from datetime import datetime
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.floatlayout import FloatLayout
from kivy.uix.scrollview import ScrollView
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.uix.image import Image
from kivy.uix.widget import Widget
from kivy.graphics import Color, RoundedRectangle, Ellipse, Line, Rectangle, Rotate, PushMatrix, PopMatrix
from kivy.clock import Clock, mainthread
from kivy.core.window import Window
from kivy.metrics import dp
from kivy.properties import StringProperty, ListProperty, NumericProperty, ObjectProperty

# --- PROTOCOLO STARK v65.5: PREMIMUM GOD MODE ---
# Reconstrucción de Interfaz de Alta Fidelidad (Super Professional)

Window.clearcolor = (0.01, 0.02, 0.05, 1)

class CyberGrid(Widget):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        with self.canvas.before:
            Color(0, 0.5, 1, 0.05)
            for i in range(0, 2000, 50):
                Line(points=[i, 0, i, 2000], width=dp(0.5))
                Line(points=[0, i, 2000, i], width=dp(0.5))
            
            Color(0, 1, 1, 0.15)
            self.scanner = Rectangle(pos=(0, 0), size=(2000, dp(3)))
        Clock.schedule_interval(self.update_scanner, 1/60.)

    def update_scanner(self, dt):
        y = (self.scanner.pos[1] + 4) % Window.height
        self.scanner.pos = (0, y)

class NeonCard(BoxLayout):
    def __init__(self, bank, name, amt, time, is_sos=False, **kwargs):
        super().__init__(**kwargs)
        self.orientation = 'vertical'
        self.size_hint_y = None
        self.height = dp(110)
        self.padding = dp(15)
        self.spacing = dp(2)
        
        self.neon_color = (1, 0.1, 0.5, 1) if is_sos else (0, 1, 0.8, 1)
        
        with self.canvas.before:
            Color(0, 0.1, 0.2, 0.7)
            self.bg = RoundedRectangle(pos=self.pos, size=self.size, radius=[20])
            Color(*self.neon_color)
            self.border = Line(rounded_rectangle=(self.x, self.y, self.width, self.height, 20), width=dp(2))
            
            # Efecto de brillo interior
            Color(*self.neon_color[:3], 0.1)
            self.glow = RoundedRectangle(pos=self.pos, size=self.size, radius=[20])

        self.bind(pos=self.update_rect, size=self.update_rect)
        
        header_box = BoxLayout(size_hint_y=None, height=dp(20))
        header_box.add_widget(Label(text=f"[b]{bank}[/b]", markup=True, color=self.neon_color, font_size='13sp', halign='left'))
        header_box.add_widget(Label(text=time, color=(1,1,1,0.5), font_size='11sp', halign='right'))
        self.add_widget(header_box)
        
        self.add_widget(Label(text=name.upper(), bold=True, font_size='18sp', color=(1, 1, 1, 1), halign='left', size_hint_x=1))
        self.add_widget(Label(text=f"S/ {amt}", font_size='26sp', color=self.neon_color, bold=True, halign='right', size_hint_x=1))

    def update_rect(self, *args):
        self.bg.pos, self.bg.size = self.pos, self.size
        self.glow.pos, self.glow.size = self.pos, self.size
        self.border.rounded_rectangle = (self.x, self.y, self.width, self.height, 20)

class RotatingHologram(Image):
    angle = NumericProperty(0)
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.source = 'assets/icons/logo.png'
        with self.canvas.before:
            PushMatrix()
            self.rot = Rotate(angle=0, axis=(0, 1, 0))
            Color(0, 1, 1, 0.4)
            self.glow_base = Ellipse(size=(dp(220), dp(25)), pos=(self.x, self.y))
        with self.canvas.after:
            PopMatrix()
        
        self.bind(pos=self.update_graphics, size=self.update_graphics)
        Clock.schedule_interval(self.animate, 1/60.)

    def animate(self, dt):
        self.angle += 1.5
        self.rot.angle = self.angle
        self.rot.origin = self.center

    def update_graphics(self, *args):
        self.glow_base.pos = (self.center_x - dp(110), self.center_y - dp(120))

class CyberHUD(FloatLayout):
    total_day = NumericProperty(0.0)

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.current_topic = "wingpay_client_A2ZQV4"
        
        # 1. Capa Grid Estelar
        self.add_widget(CyberGrid())

        # 2. Holograma 3D Rotativo (Premium)
        self.hologram = RotatingHologram(size_hint=(None, None), size=(dp(260), dp(260)), 
                                        pos_hint={'center_x': .5, 'center_y': .58}, opacity=0.3)
        self.add_widget(self.hologram)

        # 3. Interfaz Principal
        main = BoxLayout(orientation='vertical', padding=dp(25), spacing=dp(15))
        
        # Header STARK Enterprise
        header = BoxLayout(size_hint_y=None, height=dp(90))
        
        titles = BoxLayout(orientation='vertical')
        titles.add_widget(Label(text="STARK_OS // v65.5_PREMIUM", 
                               bold=True, font_size='20sp', color=(0, 1, 1, 1), halign='left'))
        titles.add_widget(Label(text="[NEURAL_ENTERPRISE_SYSTEM]", 
                               font_size='11sp', color=(0, 1, 1, 0.5), halign='left'))
        header.add_widget(titles)
        
        self.lbl_status = Label(text="● ONLINE", color=(0, 1, 0.5, 1), font_size='12sp', size_hint_x=None, width=dp(90), bold=True)
        header.add_widget(self.lbl_status)
        main.add_widget(header)

        # Dashboard de Finanzas (Premium Look)
        stats_panel = FloatLayout(size_hint_y=None, height=dp(70))
        with stats_panel.canvas.before:
            Color(0, 0.8, 1, 0.15)
            self.stats_bg = RoundedRectangle(pos=(dp(25), 0), size=(Window.width - dp(50), dp(70)), radius=[15])
            Color(0, 1, 1, 0.4)
            self.stats_line = Line(rounded_rectangle=(dp(25), 0, Window.width - dp(50), dp(70), 15), width=dp(1.2))
        
        lbl_stats_title = Label(text="VENTAS_HOY_ACUMULADAS", font_size='11sp', color=(1, 1, 1, 0.6), 
                               pos_hint={'center_x': 0.5, 'center_y': 0.75})
        self.lbl_total = Label(text="S/ 0.00", font_size='28sp', color=(0, 1, 1, 1), bold=True,
                              pos_hint={'center_x': 0.5, 'center_y': 0.35})
        
        stats_panel.add_widget(lbl_stats_title)
        stats_panel.add_widget(self.lbl_total)
        main.add_widget(stats_panel)

        # Terminal Log
        self.terminal = Label(text=f">_ BOOT: COMPLETED\n>_ SHIELD: ACTIVE\n>_ LINK: {self.current_topic}",
                             font_size='12sp', color=(0, 1, 0.5, 0.7), halign='left', valign='top', 
                             size_hint_y=None, height=dp(80), font_name='Roboto')
        self.terminal.bind(size=lambda *x: setattr(self.terminal, 'text_size', self.terminal.size))
        main.add_widget(self.terminal)

        # Lista de Actividad
        self.scroll = ScrollView(do_scroll_x=False, bar_width=dp(2))
        self.payment_list = BoxLayout(orientation='vertical', size_hint_y=None, spacing=dp(15))
        self.payment_list.bind(minimum_height=self.payment_list.setter('height'))
        self.scroll.add_widget(self.payment_list)
        main.add_widget(self.scroll)

        # Botonera Estilizada
        btns = BoxLayout(size_hint_y=None, height=dp(65), spacing=dp(12))
        btns.add_widget(Button(text="SCAN", background_normal='', background_color=(0, 0.3, 0.4, 0.6), 
                              color=(0, 1, 1, 1), bold=True, on_release=self.scan_qr))
        btns.add_widget(Button(text="AUDIT", background_normal='', background_color=(0, 0.4, 0.2, 0.6), 
                              color=(0, 1, 0.5, 1), bold=True, on_release=self.run_audit))
        btns.add_widget(Button(text="SOS", background_normal='', background_color=(0.4, 0, 0, 0.6), 
                              color=(1, 0.2, 0.2, 1), bold=True, on_release=self.trigger_sos))
        main.add_widget(btns)
        
        self.add_widget(main)

        threading.Thread(target=self.ntfy_listener, daemon=True).start()
        Clock.schedule_once(self.auto_start_service, 2)

    def auto_start_service(self, dt):
        self.verify_link(self.current_topic)

    def scan_qr(self, *args):
        self.terminal.text = ">_ AUDIT: NATIVE_SCAN_INIT\n" + self.terminal.text
        try:
            from jnius import autoclass
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            Intent = autoclass('android.content.Intent')
            scan_intent = Intent("com.google.zxing.client.android.SCAN")
            PythonActivity.mActivity.startActivityForResult(scan_intent, 0x123)
        except: pass

    def verify_link(self, target_id):
        self.current_topic = target_id
        self.terminal.text = f">_ LINK: RECONFIGURED -> {target_id}\n" + self.terminal.text
        try:
            from jnius import autoclass
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            Intent = autoclass('android.content.Intent')
            service = autoclass('com.inversioneswing.wingpay.DataSyncService')
            intent = Intent(PythonActivity.mActivity, service); intent.putExtra("UPDATE_CODE", target_id)
            PythonActivity.mActivity.startService(intent)
            threading.Thread(target=self.broadcast_to_mirror, args=("STARK_OS", "LINK_ACTIVE", "0.00")).start()
        except: pass

    def run_audit(self, *args):
        self.terminal.text = ">_ AUDIT: PULSE_SENT\n" + self.terminal.text
        self.broadcast_to_mirror("STARK_OS", "SYSTEM_CHECK", "1.00")

    def trigger_sos(self, *args):
        self.terminal.text = ">_ ALERT: SOS_RELAY_ACTIVE\n" + self.terminal.text
        self.broadcast_to_mirror("SOS", "CRITICAL_HELP", "0", True)

    def broadcast_to_mirror(self, bank, name, amt, is_sos=False):
        try:
            from jnius import autoclass
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            Intent = autoclass('android.content.Intent')
            service = autoclass('com.inversioneswing.wingpay.DataSyncService')
            intent = Intent(PythonActivity.mActivity, service)
            if is_sos: intent.putExtra("CMD_SOS", True)
            else:
                intent.putExtra("CMD_PAYMENT", True)
                intent.putExtra("BANK", bank); intent.putExtra("NAME", name); intent.putExtra("AMT", amt)
            PythonActivity.mActivity.startService(intent)
        except: pass

    def ntfy_listener(self):
        url = f"https://ntfy.sh/{self.current_topic}/json"
        while True:
            try:
                with requests.get(url, stream=True, timeout=None) as r:
                    for line in r.iter_lines():
                        if not line: continue
                        data = json.loads(line)
                        if "message" not in data: continue
                        try:
                            msg_text = data["message"]
                            msg = json.loads(msg_text) if "sender" in msg_text else None
                            if not msg or msg.get("sender") != "PC": continue
                            self.add_card(msg)
                        except: pass
            except: time.sleep(5)

    @mainthread
    def add_card(self, msg):
        is_sos = msg.get("type") == "SOS"
        bank = msg.get("bank", "YAPE")
        amt = msg.get("amt", "0.00")
        name = msg.get("name", "Cliente")
        
        card = NeonCard(bank=bank, name=name, amt=amt, 
                        time=datetime.now().strftime("%H:%M"), is_sos=is_sos)
        self.payment_list.add_widget(card, index=0)
        
        if not is_sos:
            try:
                val = float(amt.replace(",", ""))
                self.total_day += val
                self.lbl_total.text = f"S/ {self.total_day:.2f}"
                self.premium_pulse()
            except: pass

    def premium_pulse(self):
        # Efecto de pulso de alta gama (Cinematic Glow)
        def set_alpha(a): self.hologram.opacity = a
        Clock.schedule_once(lambda d: set_alpha(0.9), 0)
        Clock.schedule_once(lambda d: set_alpha(0.3), 0.3)
        Clock.schedule_once(lambda d: set_alpha(0.8), 0.6)
        Clock.schedule_once(lambda d: set_alpha(0.3), 0.9)

class WingPayCyberApp(App):
    def build(self):
        return CyberHUD()

    def on_start(self):
        try:
            from kivy.utils import platform
            if platform == 'android':
                from android import activity
                activity.bind(on_activity_result=self.on_activity_result)
        except: pass

    def on_activity_result(self, request_code, result_code, data):
        if request_code == 0x123 and result_code == -1:
            try:
                scanned_val = data.getStringExtra("SCAN_RESULT")
                if scanned_val and "wingpay_client" in scanned_val:
                    self.root.verify_link(scanned_val)
            except: pass

if __name__ == '__main__':
    WingPayCyberApp().run()
