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
from kivy.graphics import Color, RoundedRectangle, Ellipse, Line, Rectangle
from kivy.clock import Clock, mainthread
from kivy.core.window import Window
from kivy.metrics import dp
from kivy.properties import StringProperty, ListProperty, NumericProperty, ObjectProperty

# --- PROTOCOLO STARK v64.3: AUTHORITATIVE MASTER GOD ---
# Versión Profesional para Auditoría Técnica

Window.clearcolor = (0.01, 0.02, 0.05, 1)

class CyberGrid(Widget):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        with self.canvas.before:
            Color(0, 0.5, 1, 0.1)
            for i in range(0, 2000, 40):
                Line(points=[i, 0, i, 2000], width=1)
                Line(points=[0, i, 2000, i], width=1)
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
        self.neon_color = (1, 0, 0.5, 1) if is_sos else (0, 1, 1, 1)
        with self.canvas.before:
            Color(0, 0, 0, 0.6)
            self.bg = RoundedRectangle(pos=self.pos, size=self.size, radius=[15])
            Color(*self.neon_color)
            self.border = Line(rounded_rectangle=(self.x, self.y, self.width, self.height, 15), width=dp(1.5))
        self.bind(pos=self.update_rect, size=self.update_rect)
        self.add_widget(Label(text=f"[b]{bank}[/b] | {time}", markup=True, color=self.neon_color, font_size='12sp', halign='left', size_hint_x=1))
        self.add_widget(Label(text=name.upper(), bold=True, font_size='16sp', color=(1, 1, 1, 1), halign='left', size_hint_x=1))
        self.add_widget(Label(text=f"S/ {amt}", font_size='22sp', color=self.neon_color, bold=True, halign='right', size_hint_x=1))

    def update_rect(self, *args):
        self.bg.pos, self.bg.size = self.pos, self.size
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
        self.current_topic = "wingpay_client_A2ZQV4"
        self.add_widget(CyberGrid())
        self.hologram = StaticHologram(size_hint=(None, None), size=(dp(280), dp(280)), 
                                        pos_hint={'center_x': .5, 'center_y': .55}, opacity=0.15)
        self.add_widget(self.hologram)

        main = BoxLayout(orientation='vertical', padding=dp(20), spacing=dp(15))
        header = BoxLayout(size_hint_y=None, height=dp(80))
        header.add_widget(Label(text="STARK_OS // v64.3_AUTHORITATIVE\n[IMPORTACIONES_WING_CORE]", 
                               bold=True, font_size='18sp', color=(0, 1, 1, 1), halign='left'))
        self.lbl_status = Label(text="STATUS: ONLINE\nSYNC: MASTER", color=(0, 1, 0.5, 1), font_size='10sp', size_hint_x=None, width=dp(100))
        header.add_widget(self.lbl_status)
        main.add_widget(header)

        self.terminal = Label(text=f">_ STARK_CORE: v64.3_READY\n>_ ACTIVE_LINK: {self.current_topic}\n>_ STATUS: VERIFIED",
                             font_size='11sp', color=(0, 1, 0.5, 0.8), halign='left', valign='top', size_hint_y=None, height=dp(120), font_name='Roboto')
        self.terminal.bind(size=lambda *x: setattr(self.terminal, 'text_size', self.terminal.size))
        main.add_widget(self.terminal)

        self.scroll = ScrollView(do_scroll_x=False)
        self.payment_list = BoxLayout(orientation='vertical', size_hint_y=None, spacing=dp(12))
        self.payment_list.bind(minimum_height=self.payment_list.setter('height'))
        self.scroll.add_widget(self.payment_list)
        main.add_widget(self.scroll)

        btns = BoxLayout(size_hint_y=None, height=dp(60), spacing=dp(10))
        btns.add_widget(Button(text="[ SCAN_QR ]", background_color=(0, 0.5, 0.5, 0.5), color=(0, 1, 1, 1), on_release=self.scan_qr))
        btns.add_widget(Button(text="[ AUDIT_SYS ]", background_color=(0, 0.5, 0, 0.5), color=(0, 1, 0.5, 1), on_release=self.run_audit))
        btns.add_widget(Button(text="[ ALERT_SOS ]", background_color=(0.5, 0, 0, 0.5), color=(1, 0, 0, 1), on_release=self.trigger_sos))
        main.add_widget(btns)
        self.add_widget(main)

        threading.Thread(target=self.ntfy_listener, daemon=True).start()

    def scan_qr(self, *args):
        self.terminal.text = ">_ AUDIT: INITIATING_NATIVE_SCANNER\n" + self.terminal.text
        try:
            from jnius import autoclass
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            Intent = autoclass('android.content.Intent')
            scan_intent = Intent("com.google.zxing.client.android.SCAN")
            scan_intent.putExtra("SCAN_MODE", "QR_CODE_MODE")
            PythonActivity.mActivity.startActivityForResult(scan_intent, 0x123)
        except Exception as e:
            self.terminal.text = f">_ ERROR: {str(e)}\n" + self.terminal.text

    def verify_link(self, target_id):
        # --- PROTOCOLO v64.3: VINCULACIÓN PROFESIONAL ---
        self.current_topic = target_id
        self.terminal.text = f">_ STATUS: LINK_RECONFIGURED\n>_ TARGET_ID: {target_id}\n" + self.terminal.text
        try:
            from jnius import autoclass
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            Intent = autoclass('android.content.Intent')
            service = autoclass('com.inversioneswing.paymirror.StarkCaptureService')
            intent = Intent(PythonActivity.mActivity, service); intent.putExtra("UPDATE_CODE", target_id)
            PythonActivity.mActivity.startService(intent)
            threading.Thread(target=self.broadcast_to_mirror, args=("STARK_OS", "VINCULACION_CONFIRMADA", "0.00")).start()
        except: pass

    def run_audit(self, *args):
        self.terminal.text = ">_ AUDIT: SYSTEM_VERIFICATION_INITIATED\n" + self.terminal.text
        self.broadcast_to_mirror("STARK_OS", "VERIFICATION_PULSE", "1.00")

    def trigger_sos(self, *args):
        self.terminal.text = ">_ EMERGENCY: URGENT_RELAY_ACTIVE\n" + self.terminal.text
        self.broadcast_to_mirror("SOS", "CRITICAL_ALERTA", "0", True)

    def broadcast_to_mirror(self, bank, name, amt, is_sos=False):
        try:
            from jnius import autoclass
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            Intent = autoclass('android.content.Intent')
            service = autoclass('com.inversioneswing.paymirror.StarkCaptureService')
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
                            if "sender" in msg_text: msg = json.loads(msg_text)
                            else:
                                if "SOS" in msg_text.upper() or "STARK_PC_SOS" in msg_text: msg = {"type": "SOS", "sender": "PC"}
                                elif "VINCULACION_OK" in msg_text:
                                    self.terminal.text = ">_ PC_CONFIRMATION: LINK_VERIFIED\n" + self.terminal.text
                                    continue
                                else: continue
                            if msg.get("sender") == "CELULAR": continue
                            self.add_card(msg)
                        except: pass
            except: time.sleep(5)

    @mainthread
    def add_card(self, msg):
        is_sos = msg.get("type") == "SOS"
        card = NeonCard(bank=msg.get("bank", "YAPE"), name=msg.get("name", "Cliente"), 
                        amt=msg.get("amt", "0.00"), time=datetime.now().strftime("%H:%M"), is_sos=is_sos)
        self.payment_list.add_widget(card, index=len(self.payment_list.children))

class WingPayCyberApp(App):
    def build(self):
        return CyberHUD()

    def on_start(self):
        # --- [PROTOCOLO v65.1: ESCUCHA DE RESULTADOS NATIVOS] ---
        try:
            from kivy.utils import platform
            if platform == 'android':
                from android import activity
                activity.bind(on_activity_result=self.on_activity_result)
        except: pass

    def on_activity_result(self, request_code, result_code, data):
        if request_code == 0x123 and result_code == -1: # RESULT_OK
            try:
                scanned_val = data.getStringExtra("SCAN_RESULT")
                if scanned_val and "wingpay_client" in scanned_val:
                    self.root.verify_link(scanned_val)
            except: pass

if __name__ == '__main__':
    WingPayCyberApp().run()
