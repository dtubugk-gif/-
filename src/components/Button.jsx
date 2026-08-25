// כפתור בסגנון דואלינגו: פינות עגולות וצל תחתון "תלת-ממדי".

const STYLES = {
  primary: 'bg-duo-green border-duo-green-darker text-white hover:brightness-105',
  blue: 'bg-duo-blue border-duo-blue-dark text-white hover:brightness-105',
  red: 'bg-duo-red border-duo-red-dark text-white hover:brightness-105',
  white: 'bg-white border-duo-gray text-duo-text hover:bg-gray-50',
  ghost: 'bg-transparent border-transparent text-duo-blue hover:bg-sky-50',
  disabled: 'bg-duo-gray border-gray-300 text-duo-muted',
}

export default function Button({ variant = 'primary', className = '', disabled, children, ...props }) {
  const style = disabled ? STYLES.disabled : STYLES[variant]
  return (
    <button
      disabled={disabled}
      className={`btn-3d cursor-pointer rounded-2xl border-2 px-6 py-3 text-base font-extrabold ${style} ${className}`}
      {...props}
    >
      {children}
    </button>
  )
}
