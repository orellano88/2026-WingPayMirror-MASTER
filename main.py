import os
import time
import math
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.image import Image
from kivy.uix.scrollview import ScrollView
from kivy.core.window import Window
from kivy.metrics import dp
from kivy.graphics import Color, RoundedRectangle, Line
from kivy.clock import Clock, mainthread

# Fondo azul petróleo original
Window.clearcolor = (0.2, 0.35, 0.45, 1)

class FrequencyGraph(BoxLayout):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.size_hint_y = None
        self.height = dp(40)
        with self.canvas.before:
            Color(0, 1, 0, 0.5)
            self.line = Line(points=[], width=1.5)
        self.bind(pos=self.update_graphics, size=self.update_graphics)

    def update_graphics(self, *args):
        points = []
        num_points = 30
        step = self.width / max(1, num_points)
        t = time.time() * 2
        for i in range(num_points + 1):
            x = self.x + (i * step)
            # Simulación de onda de frecuencia
            y = self.y + (self.height / 2) + math.sin(i + t) * (self.height / 3) + math.cos(i*2.5 + t) * (self.height / 6)
            points.extend([x, y])
        self.line.points = points
        
    def update(self, dt):
        self.update_graphics()

class ConsoleLog(ScrollView):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.size_hint_y = 0.4
        self.layout = BoxLayout(orientation='vertical', size_hint_y=None, padding=dp(10))
        self.layout.bind(minimum_height=self.layout.setter('height'))
        
        with self.canvas.before:
            Color(0.05, 0.1, 0.15, 1) # Cuadro oscuro para la consola
            self.bg = RoundedRectangle(pos=self.pos, size=self.size, radius=[15])
        self.bind(pos=self.update_bg, size=self.update_bg)

        # Gráfico de frecuencias animado
        self.graph = FrequencyGraph()
        self.layout.add_widget(self.graph)
        Clock.schedule_interval(self.graph.update, 0.05)
        
        initial_text = (
            "[LIQUID]: Inicializando protocolos IMPORTACIONES WING...\n"
            "[LIQUID]: Núcleo listo.\n"
            "[LIQUID]: Motor Gráfico v57 Online\n"
            "[LIQUID]: Tópico: wingpay_client_A2ZQV4"
        )
        self.log_label = Label(
            text=initial_text,
            size_hint_y=None,
            halign='left',
            valign='bottom',
            color=(0, 1, 0, 1), # Verde neón puro #00FF00
            font_size='14sp',
            font_name='Roboto'
        )
        self.log_label.bind(width=lambda *x: self.log_label.setter('text_size')(self.log_label, (self.width - dp(20), None)),
                            texture_size=lambda *x: self.log_label.setter('height')(self.log_label, self.log_label.texture_size[1]))
        
        self.layout.add_widget(self.log_label)
        self.add_widget(self.layout)

    def update_bg(self, *args):
        self.bg.pos = self.pos
        self.bg.size = self.size

    @mainthread
    def add_log(self, text):
        timestamp = time.strftime("[%H:%M:%S]")
        self.log_label.text += f"\n{timestamp} {text}"
        Clock.schedule_once(self.scroll_to_bottom, 0.1)

    def scroll_to_bottom(self, *args):
        self.scroll_y = 0

class ActionButton(Button):
    def __init__(self, bg_color=(0.1, 0.25, 0.35, 1), text_color=(1, 1, 1, 1), **kwargs):
        super().__init__(**kwargs)
        self.background_normal = ''
        self.background_color = bg_color
        self.color = text_color
        self.bold = True
        self.size_hint_x = 1
        
        with self.canvas.before:
            Color(0.5, 0.6, 0.7, 0.2)
            self.border = RoundedRectangle(pos=self.pos, size=self.size, radius=[10])
        self.bind(pos=self.update_graphics, size=self.update_graphics)
        
    def update_graphics(self, *args):
        self.border.pos = self.pos
        self.border.size = self.size

class ImportacionesWingApp(App):
    def build(self):
        main_layout = BoxLayout(orientation='vertical', padding=dp(15), spacing=dp(15))

        # HEADER: Identidad y Logo
        header = BoxLayout(orientation='horizontal', size_hint_y=None, height=dp(60))
        
        # Logotipo del águila centrado/alineado
        logo = Image(source='assets/icons/logo.png', size_hint_x=None, width=dp(60))
        header.add_widget(logo)

        title_box = BoxLayout(orientation='vertical', padding=(dp(10), 0, 0, 0))
        title_box.add_widget(Label(text="IMPORTACIONES WING", color=(0, 1, 1, 1), font_size='20sp', bold=True, halign='left', text_size=(Window.width-dp(120), None)))
        title_box.add_widget(Label(text="2026 MASTER UNIVERSAL v65.0", color=(0.5, 0.8, 0.8, 1), font_size='12sp', halign='left', text_size=(Window.width-dp(120), None)))
        header.add_widget(title_box)
        
        main_layout.add_widget(header)

        # CONSOLA DINÁMICA DE FLUJOS CON GRÁFICO
        self.console = ConsoleLog()
        main_layout.add_widget(self.console)

        # FOOTER: Botonera Inferior
        footer = BoxLayout(orientation='horizontal', size_hint_y=0.1, spacing=dp(10))
        
        # 1. Botón Configuración (Transparente con borde, engranaje)
        btn_config = ActionButton(text="⚙️", bg_color=(0, 0, 0, 0))
        btn_config.bind(on_release=self.open_settings)
        footer.add_widget(btn_config)
        
        # 2. Botón QR (Cámara)
        btn_qr = ActionButton(text="📷 QR")
        btn_qr.bind(on_release=self.scan_qr)
        footer.add_widget(btn_qr)
        
        # 3. Botón TEST (Llave inglesa, verde suave)
        btn_test = ActionButton(text="🔧 TEST", text_color=(0.6, 1, 0.6, 1))
        btn_test.bind(on_release=self.run_test)
        footer.add_widget(btn_test)
        
        # 4. Botón SOS (Alerta, rojo vibrante)
        btn_sos = ActionButton(text="🚨 SOS", text_color=(1, 0.2, 0.2, 1), bg_color=(0.8, 0.1, 0.1, 1))
        btn_sos.bind(on_release=self.trigger_sos)
        footer.add_widget(btn_sos)
        
        main_layout.add_widget(footer)

        # VINCULACIÓN MANUAL (Pie de página)
        main_layout.add_widget(Label(text="VINCULACIÓN MANUAL", color=(1, 1, 1, 1), size_hint_y=None, height=dp(30), bold=True))
        main_layout.add_widget(Label(size_hint_y=0.2)) # Espacio vacío inferior

        self.start_android_service()
        return main_layout

    # --- FUNCIONES DE HARDWARE NATIVO ---

    def start_android_service(self):
        try:
            from jnius import autoclass
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            Intent = autoclass('android.content.Intent')
            service = autoclass('com.inversioneswing.wingpay.DataSyncService')
            intent = Intent(PythonActivity.mActivity, service)
            intent.putExtra("UPDATE_CODE", "wingpay_client_A2ZQV4")
            PythonActivity.mActivity.startService(intent)
            self.console.add_log("[LIQUID]: Túnel de datos establecido.")
        except Exception as e:
            self.console.add_log("[ERROR]: Modo PC/Simulador detectado.")

    def open_settings(self, instance):
        self.console.add_log("[LIQUID]: Abriendo panel de Configuración...")
        try:
            from jnius import autoclass
            Intent = autoclass('android.content.Intent')
            ComponentName = autoclass('android.content.ComponentName')
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            
            intent = Intent()
            intent.setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))
            try:
                PythonActivity.mActivity.startActivity(intent)
            except:
                intent_fallback = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                PythonActivity.mActivity.startActivity(intent_fallback)
        except Exception as e:
            self.console.add_log("[WARN]: Falla al abrir configuración nativa.")

    def scan_qr(self, instance):
        self.console.add_log("[LIQUID]: Escáner QR de sincronización abierto.")
        try:
            from jnius import autoclass
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            Intent = autoclass('android.content.Intent')
            scan_intent = Intent("com.google.zxing.client.android.SCAN")
            PythonActivity.mActivity.startActivityForResult(scan_intent, 0x123)
        except:
            self.console.add_log("[WARN]: Motor ZXing no encontrado en este dispositivo.")

    def run_test(self, instance):
        self.console.add_log("[LIQUID]: INICIANDO_TEST_TOTAL")
        try:
            from jnius import autoclass
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            Intent = autoclass('android.content.Intent')
            service = autoclass('com.inversioneswing.wingpay.DataSyncService')
            intent = Intent(PythonActivity.mActivity, service)
            intent.putExtra("CMD_PAYMENT", True)
            intent.putExtra("BANK", "SISTEMA")
            intent.putExtra("NAME", "Prueba Interna de Conexión")
            intent.putExtra("AMT", "0.00")
            PythonActivity.mActivity.startService(intent)
        except:
            pass

    def trigger_sos(self, instance):
        self.console.add_log("[LIQUID]: ALERTA: SOS Activado localmente.")
        try:
            from jnius import autoclass
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            Intent = autoclass('android.content.Intent')
            service = autoclass('com.inversioneswing.wingpay.DataSyncService')
            intent = Intent(PythonActivity.mActivity, service)
            intent.putExtra("CMD_SOS", True)
            PythonActivity.mActivity.startService(intent)
        except:
            pass

if __name__ == '__main__':
    ImportacionesWingApp().run()