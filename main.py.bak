import os
import threading
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.core.window import Window
from kivy.metrics import dp
from kivy.graphics import Color, Rectangle

# --- PROTOCOLO STARK: WING SENTINEL OMEGA SOS ---
# Interfaz Táctica (Basada exactamente en la captura original)

# Fondo Negro Puro (Consumo mínimo de batería)
Window.clearcolor = (0, 0, 0, 1)

class SentinelButton(Button):
    def __init__(self, bg_color, **kwargs):
        super().__init__(**kwargs)
        self.background_normal = ''
        self.background_color = bg_color
        self.bold = True
        self.font_size = '18sp'
        self.size_hint_y = None
        self.height = dp(70)

class WingSentinelApp(App):
    def build(self):
        # Contenedor Principal
        main_layout = BoxLayout(orientation='vertical', padding=dp(20), spacing=dp(15))

        # Espaciador superior para empujar todo hacia el centro
        main_layout.add_widget(Label(size_hint_y=0.3))

        # TÍTULOS (Rojo Intenso)
        title_box = BoxLayout(orientation='vertical', size_hint_y=None, height=dp(100))
        title_1 = Label(text="WING SENTINEL v5.7", color=(1, 0, 0, 1), font_size='26sp', bold=True)
        title_2 = Label(text="OMEGA SOS", color=(1, 0, 0, 1), font_size='26sp', bold=True)
        title_box.add_widget(title_1)
        title_box.add_widget(title_2)
        main_layout.add_widget(title_box)

        # BOTÓN 1: PÁNICO SOS (ROJO PURO)
        btn_sos = SentinelButton(bg_color=(1, 0, 0, 1), text="🚨 BOTÓN DE PÁNICO SOS 🚨", font_size='22sp')
        btn_sos.height = dp(90)
        btn_sos.bind(on_release=self.trigger_sos)
        main_layout.add_widget(btn_sos)

        # BOTÓN 2: PROBAR VOZ DE JARVIS (GRIS OSCURO)
        btn_voice = SentinelButton(bg_color=(0.2, 0.2, 0.2, 1), text="🔊 PROBAR VOZ DE JARVIS")
        btn_voice.bind(on_release=self.test_voice)
        main_layout.add_widget(btn_voice)

        # BOTÓN 3: CONFIGURAR INICIO AUTOMÁTICO (AZUL/CYAN OSCURO)
        btn_auto = SentinelButton(bg_color=(0.1, 0.4, 0.5, 1), text="CONFIGURAR INICIO AUTOMÁTICO (HUAWEI)", font_size='16sp')
        btn_auto.bind(on_release=self.open_autostart_settings)
        main_layout.add_widget(btn_auto)

        # BOTÓN 4: REINICIAR OÍDO DE JARVIS (GRIS OSCURO)
        btn_restart = SentinelButton(bg_color=(0.2, 0.2, 0.2, 1), text="REINICIAR OÍDO DE JARVIS")
        btn_restart.bind(on_release=self.restart_listener)
        main_layout.add_widget(btn_restart)

        # Espaciador inferior
        main_layout.add_widget(Label(size_hint_y=0.4))

        # Auto-iniciar el servicio de Android al abrir la app
        self.start_android_service()

        return main_layout

    # --- LÓGICA DE HARDWARE NATIVA (GLM) ---

    def start_android_service(self):
        try:
            from jnius import autoclass
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            Intent = autoclass('android.content.Intent')
            service = autoclass('com.inversioneswing.wingpay.DataSyncService')
            intent = Intent(PythonActivity.mActivity, service)
            intent.putExtra("UPDATE_CODE", "wingpay_client_A2ZQV4")
            PythonActivity.mActivity.startService(intent)
        except Exception as e:
            print("Entorno no-Android:", e)

    def trigger_sos(self, instance):
        try:
            from jnius import autoclass
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            Intent = autoclass('android.content.Intent')
            service = autoclass('com.inversioneswing.wingpay.DataSyncService')
            intent = Intent(PythonActivity.mActivity, service)
            intent.putExtra("CMD_SOS", True)
            PythonActivity.mActivity.startService(intent)
        except Exception as e:
            print("SOS SIMULADO (PC):", e)

    def test_voice(self, instance):
        try:
            from jnius import autoclass
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            Intent = autoclass('android.content.Intent')
            service = autoclass('com.inversioneswing.wingpay.DataSyncService')
            # Simulando un pago para probar la voz
            intent = Intent(PythonActivity.mActivity, service)
            intent.putExtra("CMD_PAYMENT", True)
            intent.putExtra("BANK", "SISTEMA")
            intent.putExtra("NAME", "Prueba de Audio JARVIS")
            intent.putExtra("AMT", "0.00")
            PythonActivity.mActivity.startService(intent)
        except Exception as e:
            print("VOZ SIMULADA:", e)

    def open_autostart_settings(self, instance):
        # Lógica para abrir configuraciones de Huawei/Xiaomi (Evitar cierre del servicio)
        try:
            from jnius import autoclass
            Intent = autoclass('android.content.Intent')
            ComponentName = autoclass('android.content.ComponentName')
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            
            intent = Intent()
            # Huawei
            intent.setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))
            try:
                PythonActivity.mActivity.startActivity(intent)
            except:
                # Xiaomi Fallback
                intent.setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
                PythonActivity.mActivity.startActivity(intent)
        except Exception as e:
            print("ABRIR AJUSTES:", e)

    def restart_listener(self, instance):
        # Lanza el intent de configuración de acceso a notificaciones para que el usuario pueda apagar y prender el permiso
        try:
            from jnius import autoclass
            Intent = autoclass('android.content.Intent')
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            PythonActivity.mActivity.startActivity(intent)
        except Exception as e:
            print("REINICIO OÍDO:", e)


if __name__ == '__main__':
    WingSentinelApp().run()
