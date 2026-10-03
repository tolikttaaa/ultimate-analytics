/** A displayed value with its unit set smaller, e.g. `29.7` and ` km/h`; values without a unit stay as they are. */
export function MetricValue({ value }: { value: string }) {
  const match = /^([\d:.,–-]+)\s+(.+)$/.exec(value)
  if (!match) return <>{value}</>
  return (
    <>
      {match[1]}
      <span className="unit"> {match[2]}</span>
    </>
  )
}
