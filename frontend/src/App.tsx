import { Route, Routes } from 'react-router'

// Screens arrive in the next step; this only proves routing and Tailwind are wired.
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<main className="p-6 text-xl font-semibold">BillSync</main>} />
    </Routes>
  )
}
