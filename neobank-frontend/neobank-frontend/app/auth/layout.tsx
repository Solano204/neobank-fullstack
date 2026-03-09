export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-[#07070e] flex">
      {/* Left decorative panel */}
      <div className="hidden lg:flex flex-col justify-between w-[45%] relative overflow-hidden bg-gradient-to-br from-[#0f0f1a] via-[#111827] to-[#0d1b2a] p-12">
        {/* Glow effects */}
        <div className="absolute top-0 left-0 w-80 h-80 bg-blue-600/8 rounded-full blur-3xl -translate-x-1/2 -translate-y-1/2" />
        <div className="absolute bottom-0 right-0 w-96 h-96 bg-cyan-500/6 rounded-full blur-3xl translate-x-1/2 translate-y-1/2" />

        {/* Logo */}
        <div className="flex items-center gap-3 relative z-10">
          <div className="w-10 h-10 rounded-xl bg-blue-600 flex items-center justify-center">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
            </svg>
          </div>
          <span className="font-display font-bold text-xl text-white">NeoBank</span>
        </div>

        {/* Center content */}
        <div className="relative z-10">
          <h2 className="font-display text-4xl font-bold text-white leading-tight mb-4">
            Banca digital para<br />
            <span className="gradient-text">el México moderno</span>
          </h2>
          <p className="text-slate-400 text-lg leading-relaxed">
            Transferencias instantáneas, seguridad avanzada y control total de tus finanzas.
          </p>
          <div className="mt-8 flex flex-col gap-3">
            {["Transferencias SPEI en segundos", "KYC 100% digital y seguro", "Soporte con IA 24/7"].map(f => (
              <div key={f} className="flex items-center gap-3">
                <div className="w-5 h-5 rounded-full bg-blue-500/20 border border-blue-500/40 flex items-center justify-center flex-shrink-0">
                  <svg width="10" height="10" viewBox="0 0 12 12" fill="none"><path d="M2 6l3 3 5-5" stroke="#3b82f6" strokeWidth="2" strokeLinecap="round"/></svg>
                </div>
                <span className="text-slate-300 text-sm">{f}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Footer */}
        <p className="text-slate-600 text-sm relative z-10">© 2025 NeoBank. Todos los derechos reservados.</p>
      </div>

      {/* Right: form */}
      <div className="flex-1 flex items-center justify-center p-6 lg:p-12">
        <div className="w-full max-w-md">
          {children}
        </div>
      </div>
    </div>
  );
}
